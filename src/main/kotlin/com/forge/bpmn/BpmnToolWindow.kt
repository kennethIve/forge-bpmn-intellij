package com.forge.bpmn

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class BpmnToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val explorer = BpmnExplorer(project)
        val content = ContentFactory.getInstance().createContent(explorer, "", false)
        content.setDisposer(explorer)
        toolWindow.contentManager.addContent(content)
    }
}

private data class BpmnNode(
    val name: String,
    val file: VirtualFile?,
    val kind: Kind,
) {
    enum class Kind { ROOT, DIR, FILE, EMPTY }
    override fun toString(): String = name
}

class BpmnExplorer(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val tree = Tree()

    init {
        tree.isRootVisible = true
        tree.showsRootHandles = true
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                tree: JTree,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean,
            ) {
                val node = (value as? DefaultMutableTreeNode)?.userObject as? BpmnNode ?: return
                append(node.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                icon = when (node.kind) {
                    BpmnNode.Kind.FILE -> BpmnIcons.FILE
                    BpmnNode.Kind.ROOT -> AllIcons.Nodes.Module
                    else -> AllIcons.Nodes.Folder
                }
            }
        }
        TreeSpeedSearch.installOn(tree)
        TreeUtil.installActions(tree)
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                openSelected()
                return true
            }
        }.installOn(tree)
        tree.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) openSelected()
            }
        })

        val refresh = object : AnAction("Refresh", "Reload BPMN files", AllIcons.Actions.Refresh), DumbAware {
            override fun actionPerformed(e: AnActionEvent) {
                reload()
            }
        }
        val toolbar = ActionManager.getInstance().createActionToolbar(
            "ForgeBpmnExplorer",
            DefaultActionGroup(refresh),
            true,
        )
        toolbar.targetComponent = this

        add(toolbar.component, BorderLayout.NORTH)
        add(JScrollPane(tree).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)

        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { it.path.endsWith(".bpmn", ignoreCase = true) }) reload()
                }
            },
        )
        reload()
    }

    private fun openSelected() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val data = node.userObject as? BpmnNode ?: return
        val file = data.file ?: return
        if (data.kind != BpmnNode.Kind.FILE) return
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun reload() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val files = collectBpmnFiles()
            val root = buildTree(files)
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                tree.model = DefaultTreeModel(root)
                TreeUtil.expandAll(tree)
            }
        }
    }

    private fun collectBpmnFiles(): List<VirtualFile> {
        val base = project.guessProjectDir() ?: return emptyList()
        val skip = setOf("build", ".gradle", "out", ".git", "node_modules", ".idea", "dist", "target")
        val found = mutableListOf<VirtualFile>()
        VfsUtilCore.iterateChildrenRecursively(
            base,
            { vf -> !vf.isDirectory || vf.name !in skip },
            { vf ->
                if (!vf.isDirectory && vf.extension.equals("bpmn", ignoreCase = true)) found.add(vf)
                true
            },
        )
        return found.sortedBy { it.path }
    }

    private fun buildTree(files: List<VirtualFile>): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode(BpmnNode(project.name, null, BpmnNode.Kind.ROOT))
        if (files.isEmpty()) {
            root.add(DefaultMutableTreeNode(BpmnNode("No .bpmn files", null, BpmnNode.Kind.EMPTY)))
            return root
        }
        val dirs = mutableMapOf("" to root)
        val base = project.guessProjectDir()
        for (file in files) {
            val rel = (base?.let { VfsUtilCore.getRelativePath(file, it) } ?: file.name).trim('/')
            val parts = rel.split('/')
            var key = ""
            var parent = root
            for (i in 0 until parts.lastIndex) {
                key = if (key.isEmpty()) parts[i] else "$key/${parts[i]}"
                val existing = dirs[key]
                if (existing != null) {
                    parent = existing
                } else {
                    val dir = DefaultMutableTreeNode(BpmnNode(parts[i], null, BpmnNode.Kind.DIR))
                    parent.add(dir)
                    dirs[key] = dir
                    parent = dir
                }
            }
            parent.add(DefaultMutableTreeNode(BpmnNode(file.name, file, BpmnNode.Kind.FILE)))
        }
        return root
    }

    override fun dispose() {}
}
