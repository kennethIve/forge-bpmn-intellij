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

    fun isCaretNavigateSuppressed(): Boolean =
        System.currentTimeMillis() < suppressCaretNavigateUntilMs

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
                        return@invokeLater
                    }
                }
            } catch (_: Exception) {
                // Swallow tab/CEF races; selection is best-effort.
            }
        }, ModalityState.defaultModalityState(), project.disposed)
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
                    // Second pass after tab selection settles (composite editor race).
                    ApplicationManager.getApplication().invokeLater({
                        if (project.isDisposed || !file.isValid) return@invokeLater
                        fem.setSelectedEditor(file, "forge-bpmn-xml")
                        forceXmlCaret(fem, file, offset)
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
        return ReadAction.compute<Int?, RuntimeException> {
            val psi = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@compute null
            val root = psi.rootTag ?: return@compute null
            val attr = findIdAttribute(root, elementId) ?: return@compute null
            // Prefer the value token so the caret sits on the id text (matches Problems F4).
            attr.valueElement?.textRange?.startOffset ?: attr.textRange.startOffset
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
