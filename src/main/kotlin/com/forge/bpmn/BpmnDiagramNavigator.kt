package com.forge.bpmn

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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
                fem.setSelectedEditor(file, "forge-bpmn-xml")
                if (offset != null) {
                    OpenFileDescriptor(project, file, offset).navigate(true)
                    fem.setSelectedEditor(file, "forge-bpmn-xml")
                    for (editor in fem.getEditors(file)) {
                        if (editor is TextEditor && editor.getName() == "XML") {
                            editor.editor.caretModel.moveToOffset(offset)
                            editor.editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // Best-effort XML navigation.
            }
        }, ModalityState.defaultModalityState(), project.disposed)
    }

    private fun findIdAttributeOffset(project: Project, file: VirtualFile, elementId: String): Int? {
        val psi = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return null
        val root = psi.rootTag ?: return null
        val attr = findIdAttribute(root, elementId) ?: return null
        // Prefer the value token so the caret sits on the id text (matches Problems F4).
        return attr.valueElement?.textRange?.startOffset ?: attr.textRange.startOffset
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
