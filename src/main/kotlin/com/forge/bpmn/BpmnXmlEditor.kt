package com.forge.bpmn

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlToken
import javax.swing.SwingUtilities

/**
 * XML tab wrapper. Caret on an element `id` attribute selects that node on the diagram
 * canvas so Problems-row navigation (which lands on the id attr) also selects the shape.
 */
class BpmnXmlEditor(
    private val file: VirtualFile,
    private val delegate: TextEditor,
) : TextEditor by delegate {
    init {
        val editor = delegate.editor
        editor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                SwingUtilities.invokeLater { syncSelectionFromCaret() }
            }
        })
    }

    private fun syncSelectionFromCaret() {
        val project = delegate.editor.project ?: return
        val document = delegate.editor.document
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
        val offset = delegate.editor.caretModel.offset
        val el = psiFile.findElementAt(offset) ?: return
        val id = resolveElementId(el) ?: return
        for (fe in com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getEditors(file)) {
            if (fe is BpmnFileEditor) {
                fe.selectElement(id)
                return
            }
        }
    }

    private fun resolveElementId(el: com.intellij.psi.PsiElement): String? {
        var cur: com.intellij.psi.PsiElement? = el
        while (cur != null) {
            when (cur) {
                is XmlAttributeValue -> {
                    val attr = cur.parent as? XmlAttribute
                    if (attr?.localName == "id") return attr.value
                }
                is XmlAttribute -> if (cur.localName == "id") return cur.value
                is XmlToken -> { /* keep walking */ }
            }
            cur = cur.parent
        }
        return null
    }

    override fun getName(): String = "XML"
    override fun getFile(): VirtualFile = file
}
