package com.forge.bpmn

import com.intellij.analysis.problemsView.toolWindow.ProblemNode
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
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
import java.awt.AWTEvent
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import javax.swing.event.TreeSelectionListener

/**
 * Problems File-tab rows only navigate on double-click / F4 unless Autoscroll to Source
 * is on. UX wants a single click to select the canvas element: listen to the Problems
 * tree selection (public [ProblemsViewPanel.getTree]) and, when the change is
 * user-driven (mouse press on the tree, or keyboard focus already in the tree),
 * resolve the highlight's `id` attribute and call [BpmnDiagramNavigator].
 *
 * Do **not** require focus to already be inside the Problems tree: after a navigate
 * that moves focus to XML, the next row click must still move the caret. An
 * [AWTEventListener] marks mouse-presses over the tree *before* focus transfers,
 * so the selection listener is not gated on current focus owner.
 *
 * Avoids internal [com.intellij.analysis.problemsView.toolWindow.HighlightingProblem] —
 * uses public [ProblemNode] line/column instead.
 */
object BpmnProblemsSelectionHook {
    private val INSTALLED = Key.create<Boolean>("forge.bpmn.problems.hook.installed")
    private const val TREE_LISTENER_PROP = "forge.bpmn.problems.tree.listener"
    private const val TREE_AWT_PROP = "forge.bpmn.problems.tree.awt"
    /** How long after a mouse-press on the tree we treat selection changes as user-driven. */
    private const val USER_GESTURE_MS = 750L

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

        // Mark user clicks before focus moves to the tree (selection often fires while
        // focus is still on the XML editor after a prior navigate).
        val gestureUntilMs = longArrayOf(0L)
        val awtListener = AWTEventListener { event ->
            if (event !is MouseEvent || event.id != MouseEvent.MOUSE_PRESSED) return@AWTEventListener
            val src = event.component ?: return@AWTEventListener
            if (SwingUtilities.isDescendingFrom(src, tree)) {
                gestureUntilMs[0] = System.currentTimeMillis() + USER_GESTURE_MS
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(awtListener, AWTEvent.MOUSE_EVENT_MASK)
        tree.putClientProperty(TREE_AWT_PROP, awtListener)

        var lastHandledKey: String? = null

        val listener = TreeSelectionListener { ev ->
            if (project.isDisposed) return@TreeSelectionListener

            val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            val focusInTree = focus != null && SwingUtilities.isDescendingFrom(focus, tree)
            val userGesture = System.currentTimeMillis() <= gestureUntilMs[0]
            // User click (even with focus still on XML) or keyboard nav while tree focused.
            // Daemon tree rebuilds that reselect a node are ignored.
            if (!userGesture && !focusInTree) return@TreeSelectionListener

            val path = ev?.newLeadSelectionPath ?: tree.selectionPath ?: return@TreeSelectionListener
            val node = path.lastPathComponent as? ProblemNode ?: return@TreeSelectionListener
            val file = node.file
            if (!"bpmn".equals(file.extension, ignoreCase = true)) return@TreeSelectionListener

            val elementId = resolveElementId(project, node) ?: return@TreeSelectionListener
            val key = "${file.path}#$elementId"
            // Same lead re-fired by refresh while focus stays in the tree: skip.
            // Different id (A→B) always navigates; openXmlAtElementId forces caret.
            if (!userGesture && key == lastHandledKey) return@TreeSelectionListener
            lastHandledKey = key

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
        // commitDocument may need write access — keep it outside ReadAction.
        PsiDocumentManager.getInstance(project).commitDocument(document)
        return ApplicationManager.getApplication().runReadAction(Computable {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@Computable null
            val el = psiFile.findElementAt(offset) ?: return@Computable null
            resolveIdAttribute(el)
        })
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
