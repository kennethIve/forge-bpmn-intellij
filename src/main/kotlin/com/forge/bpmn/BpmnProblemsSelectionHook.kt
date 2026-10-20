package com.forge.bpmn

import com.intellij.analysis.problemsView.toolWindow.ProblemNode
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlToken
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import java.awt.KeyboardFocusManager
import javax.swing.SwingUtilities
import javax.swing.event.TreeSelectionListener

/**
 * Problems File-tab rows only navigate on double-click / F4 unless Autoscroll to Source
 * is on. UX wants a single click to select the canvas element: listen to the Problems
 * tree selection (public [ProblemsViewPanel.getTree]) and, when the click focused the
 * tree, resolve the highlight's `id` attribute and call [BpmnDiagramNavigator].
 *
 * Avoids internal [com.intellij.analysis.problemsView.toolWindow.HighlightingProblem] —
 * uses public [ProblemNode] line/column instead.
 */
object BpmnProblemsSelectionHook {
    private val INSTALLED = Key.create<Boolean>("forge.bpmn.problems.hook.installed")
    private const val TREE_LISTENER_PROP = "forge.bpmn.problems.tree.listener"

    fun install(project: Project) {
        if (project.isDisposed) return
        if (project.getUserData(INSTALLED) == true) return
        project.putUserData(INSTALLED, true)

        val connection = project.messageBus.connect(project)
        connection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun toolWindowShown(toolWindow: ToolWindow) {
                if (toolWindow.id == ProblemsView.ID) {
                    attachAll(project)
                }
            }

            override fun stateChanged(toolWindowManager: com.intellij.openapi.wm.ToolWindowManager) {
                attachAll(project)
            }
        })

        ProblemsView.getToolWindow(project)?.contentManager?.addContentManagerListener(
            object : ContentManagerListener {
                override fun contentAdded(event: ContentManagerEvent) {
                    attachAll(project)
                }

                override fun selectionChanged(event: ContentManagerEvent) {
                    attachAll(project)
                }
            },
        )

        ApplicationManager.getApplication().invokeLater(
            { attachAll(project) },
            ModalityState.any(),
            project.disposed,
        )
    }

    private fun attachAll(project: Project) {
        if (project.isDisposed) return
        val tw = ProblemsView.getToolWindow(project) ?: return
        for (content in tw.contentManager.contents) {
            val panel = content.component as? ProblemsViewPanel ?: continue
            attachToTree(project, panel)
        }
        ProblemsView.getSelectedPanel(project)?.let { attachToTree(project, it) }
    }

    private fun attachToTree(project: Project, panel: ProblemsViewPanel) {
        val tree = panel.tree
        if (tree.getClientProperty(TREE_LISTENER_PROP) != null) return
        val listener = TreeSelectionListener { ev ->
            if (project.isDisposed) return@TreeSelectionListener
            // Only user clicks in the Problems tree — ignore selection restores from daemon refresh
            val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (focus == null || !SwingUtilities.isDescendingFrom(focus, tree)) return@TreeSelectionListener

            val path = ev?.newLeadSelectionPath ?: tree.selectionPath ?: return@TreeSelectionListener
            val node = path.lastPathComponent as? ProblemNode ?: return@TreeSelectionListener
            val file = node.file
            if (!"bpmn".equals(file.extension, ignoreCase = true)) return@TreeSelectionListener

            val elementId = resolveElementId(project, node) ?: return@TreeSelectionListener
            BpmnDiagramNavigator.select(project, file, elementId)
        }
        tree.putClientProperty(TREE_LISTENER_PROP, listener)
        tree.addTreeSelectionListener(listener)
    }

    private fun resolveElementId(project: Project, node: ProblemNode): String? {
        val file = node.file
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
        // Prefer OpenFileDescriptor (public); ProblemNode.line/column are private Kotlin props.
        val descriptor = node.descriptor
        val line = descriptor.line
        val column = descriptor.column
        if (line < 0 || line >= document.lineCount) return null
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        val offset = (lineStart + column.coerceAtLeast(0)).coerceIn(lineStart, lineEnd)
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val el = psiFile.findElementAt(offset) ?: return null
        return resolveIdAttribute(el)
    }

    private fun resolveIdAttribute(el: com.intellij.psi.PsiElement): String? {
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
}
