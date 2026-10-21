package com.forge.bpmn

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

object BpmnDiagramNavigator {
    @Volatile
    private var suppressCaretNavigateUntilMs: Long = 0L

    /** Skip XML caret → canvas select while we are applying canvas → XML sync. */
    @Volatile
    private var suppressCaretSelectUntilMs: Long = 0L

    fun isCaretNavigateSuppressed(): Boolean =
        System.currentTimeMillis() < suppressCaretNavigateUntilMs

    fun isCaretSelectSuppressed(): Boolean =
        System.currentTimeMillis() < suppressCaretSelectUntilMs

    fun select(project: Project, file: VirtualFile, elementId: String?) {
        if (elementId.isNullOrBlank() || !file.isValid || project.isDisposed) return
        // Defer off the Problems tree selection / caret callback to avoid nested
        // navigate→tab-switch races that surface as a generic "IDE error occurred" toast.
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed || !file.isValid) return@invokeLater
            try {
                val fem = FileEditorManager.getInstance(project)
                if (!fem.isFileOpen(file)) {
                    OpenFileDescriptor(project, file).navigate(true)
                }
                fem.setSelectedEditor(file, "forge-bpmn-diagram")
                for (editor in fem.getEditors(file)) {
                    if (editor is BpmnFileEditor) {
                        // Prefer canvas select when a bpmn-js shape exists; otherwise
                        // JS calls back and we open XML at the element id attribute.
                        editor.selectElement(elementId, fallbackToXml = true)
                        // Quiet XML caret + highlight; suppress caret→canvas bounce.
                        suppressCaretSelectUntilMs = System.currentTimeMillis() + 500
                        highlightXmlId(project, file, elementId, moveCaret = true, requestFocus = false)
                        return@invokeLater
                    }
                }
            } catch (_: Exception) {
                // Swallow tab/CEF races; selection is best-effort.
            }
        }, ModalityState.defaultModalityState(), project.disposed)
    }

    /**
     * Canvas → XML: user selected a shape/flow on the diagram. Move XML caret to
     * the element's `id` value and apply solid `#3574F0` highlight — do **not**
     * steal focus or switch tabs (user stays on the diagram).
     */
    fun syncXmlFromCanvas(project: Project, file: VirtualFile, elementId: String?) {
        if (elementId.isNullOrBlank() || !file.isValid || project.isDisposed) return
        suppressCaretSelectUntilMs = System.currentTimeMillis() + 500
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed || !file.isValid) return@invokeLater
            try {
                highlightXmlId(project, file, elementId, moveCaret = true, requestFocus = false)
            } catch (_: Exception) {
                // Best-effort sync.
            }
        }, ModalityState.defaultModalityState(), project.disposed)
    }

    fun clearXmlSelectionHighlight(project: Project, file: VirtualFile) {
        if (!file.isValid || project.isDisposed) return
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed || !file.isValid) return@invokeLater
            try {
                BpmnXmlSelectionHighlight.clearForFile(file, FileEditorManager.getInstance(project))
            } catch (_: Exception) {
            }
        }, ModalityState.defaultModalityState(), project.disposed)
    }

    /**
     * Highlight (and optionally move caret to) the `id` attribute value in XML.
     * Does not switch editor tabs unless [requestFocus] is true.
     */
    fun highlightXmlId(
        project: Project,
        file: VirtualFile,
        elementId: String?,
        moveCaret: Boolean,
        requestFocus: Boolean,
    ) {
        if (elementId.isNullOrBlank() || !file.isValid || project.isDisposed) return
        val range = findIdAttributeRange(project, file, elementId) ?: return
        val fem = FileEditorManager.getInstance(project)
        for (editor in BpmnXmlSelectionHighlight.xmlEditors(file, fem)) {
            BpmnXmlSelectionHighlight.apply(editor, range.first, range.second)
            if (moveCaret) {
                BpmnXmlSelectionHighlight.moveCaretQuiet(editor, range.first, scroll = true)
                if (requestFocus) {
                    editor.contentComponent.requestFocusInWindow()
                }
            }
        }
    }

    /**
     * No bpmn-js shape for [elementId] (e.g. dangling sequence flow stripped on import).
     * Switch to the XML tab and place the caret on that element's `id` attribute —
     * do not pretend bendpoints exist on the canvas.
     *
     * Always force caret + scroll even when the XML tab / file is already focused
     * (consecutive Problems clicks must not no-op).
     */
    fun openXmlAtElementId(project: Project, file: VirtualFile, elementId: String?) {
        if (elementId.isNullOrBlank() || !file.isValid || project.isDisposed) return
        suppressCaretNavigateUntilMs = System.currentTimeMillis() + 800
        suppressCaretSelectUntilMs = System.currentTimeMillis() + 800
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed || !file.isValid) return@invokeLater
            try {
                val offset = findIdAttributeOffset(project, file, elementId)
                val fem = FileEditorManager.getInstance(project)
                if (!fem.isFileOpen(file)) {
                    OpenFileDescriptor(project, file).navigate(true)
                }
                // Ensure XML tab is selected before forcing caret (idempotent if already there).
                fem.setSelectedEditor(file, "forge-bpmn-xml")
                if (offset != null) {
                    // Prefer direct caret move: OpenFileDescriptor.navigate can no-op when
                    // the file/editor is already open on a different offset.
                    forceXmlCaret(fem, file, offset)
                    highlightXmlId(project, file, elementId, moveCaret = false, requestFocus = false)
                    // Second pass after tab selection settles (composite editor race).
                    ApplicationManager.getApplication().invokeLater({
                        if (project.isDisposed || !file.isValid) return@invokeLater
                        fem.setSelectedEditor(file, "forge-bpmn-xml")
                        forceXmlCaret(fem, file, offset)
                        highlightXmlId(project, file, elementId, moveCaret = false, requestFocus = false)
                    }, ModalityState.defaultModalityState(), project.disposed)
                }
            } catch (_: Exception) {
                // Best-effort XML navigation.
            }
        }, ModalityState.defaultModalityState(), project.disposed)
    }

    private fun forceXmlCaret(fem: FileEditorManager, file: VirtualFile, offset: Int) {
        for (editor in fem.getEditors(file)) {
            if (editor is TextEditor && editor.getName() == "XML") {
                val ed = editor.editor
                ed.caretModel.moveToOffset(offset)
                ed.selectionModel.removeSelection()
                ed.scrollingModel.scrollToCaret(ScrollType.CENTER)
                ed.contentComponent.requestFocusInWindow()
                return
            }
        }
        // Fallback: any TextEditor for this file (provider edge cases).
        for (editor in fem.getEditors(file)) {
            if (editor is TextEditor) {
                val ed = editor.editor
                ed.caretModel.moveToOffset(offset)
                ed.selectionModel.removeSelection()
                ed.scrollingModel.scrollToCaret(ScrollType.CENTER)
                ed.contentComponent.requestFocusInWindow()
                return
            }
        }
    }

    private fun findIdAttributeOffset(project: Project, file: VirtualFile, elementId: String): Int? {
        return findIdAttributeRange(project, file, elementId)?.first
    }

    /** Inclusive-exclusive offsets of the `id` attribute value (quoted text range). */
    private fun findIdAttributeRange(project: Project, file: VirtualFile, elementId: String): Pair<Int, Int>? {
        return ReadAction.compute<Pair<Int, Int>?, RuntimeException> {
            val psi = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@compute null
            val root = psi.rootTag ?: return@compute null
            val attr = findIdAttribute(root, elementId) ?: return@compute null
            val value = attr.valueElement
            if (value != null) {
                Pair(value.textRange.startOffset, value.textRange.endOffset)
            } else {
                Pair(attr.textRange.startOffset, attr.textRange.endOffset)
            }
        }
    }

    private fun findIdAttribute(root: XmlTag, id: String): XmlAttribute? {
        val stack = ArrayDeque<XmlTag>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val tag = stack.removeFirst()
            val attr = tag.getAttribute("id")
            if (attr?.value == id) return attr
            stack.addAll(tag.subTags)
        }
        return null
    }
}
