package com.forge.bpmn

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlToken
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.SwingUtilities

/**
 * XML tab wrapper. Caret on an element `id` attribute selects that node on the diagram
 * canvas so Problems-row navigation (which lands on the id attr) also selects the shape.
 *
 * If the XML editor gained focus immediately before the caret move (typical Problems /
 * Navigate click), switch to the diagram tab as well. Pure caret moves while already
 * editing XML only update canvas selection — no tab steal.
 */
class BpmnXmlEditor(
    private val file: VirtualFile,
    private val delegate: TextEditor,
) : TextEditor by delegate {
    @Volatile
    private var focusedAtMs: Long = 0L

    init {
        val editor = delegate.editor
        editor.contentComponent.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                focusedAtMs = System.currentTimeMillis()
            }
        })
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
        val fromNavigate = System.currentTimeMillis() - focusedAtMs < 300
        if (fromNavigate) {
            BpmnDiagramNavigator.select(project, file, id)
        } else {
            for (fe in com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getEditors(file)) {
                if (fe is BpmnFileEditor) {
                    fe.selectElement(id)
                    return
                }
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
