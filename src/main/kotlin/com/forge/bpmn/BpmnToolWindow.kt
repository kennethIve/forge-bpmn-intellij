package com.forge.bpmn

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class BpmnToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun shouldBeAvailable(project: Project): Boolean = true

    override fun init(toolWindow: ToolWindow) {
        toolWindow.setToHideOnEmptyContent(false)
        toolWindow.isAvailable = true
        toolWindow.isShowStripeButton = true
        toolWindow.stripeTitle = "BPMN"
        toolWindow.setIcon(BpmnIcons.FILE)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val explorer = BpmnExplorer(project, toolWindow.disposable)
        val content = ContentFactory.getInstance().createContent(explorer, "", false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
        toolWindow.isAvailable = true
        toolWindow.isShowStripeButton = true
    }
}

class ActivateBpmnToolWindowAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ToolWindowManager.getInstance(project).getToolWindow("BPMN")?.show()
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

class BpmnExplorer(
    private val project: Project,
    parent: Disposable,
) : JPanel(BorderLayout()) {
    private val tree = Tree()
    private val treeScroll = JScrollPane(tree).apply { border = JBUI.Borders.empty() }
    private val emptyPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(24)
        background = UIUtil.getPanelBackground()
        val title = JBLabel("No .bpmn files yet").apply {
            alignmentX = CENTER_ALIGNMENT
            horizontalAlignment = SwingConstants.CENTER
            foreground = UIUtil.getContextHelpForeground()
        }
        val openExample = JButton("Open example").apply {
            alignmentX = CENTER_ALIGNMENT
            addActionListener { openExampleFile() }
        }
        val hint = JBLabel("Opens refund-request.bpmn").apply {
            alignmentX = CENTER_ALIGNMENT
            horizontalAlignment = SwingConstants.CENTER
            foreground = UIUtil.getContextHelpForeground()
            font = JBUI.Fonts.smallFont()
        }
        add(Box.createVerticalGlue())
        add(title)
        add(Box.createVerticalStrut(12))
        add(openExample)
        add(Box.createVerticalStrut(8))
        add(hint)
        add(Box.createVerticalGlue())
    }
    private val center = JPanel(BorderLayout())

    init {
        tree.isRootVisible = true
        tree.showsRootHandles = true
        tree.emptyText.text = "No .bpmn files yet"
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

        val refresh = object : DumbAwareAction("Refresh", "Reload BPMN files", AllIcons.Actions.Refresh) {
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

        center.add(treeScroll, BorderLayout.CENTER)
        add(toolbar.component, BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)

        project.messageBus.connect(parent).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any { it.path.endsWith(".bpmn", ignoreCase = true) }) reload()
                }
            },
        )
        reload()
    }

    private fun showEmpty(empty: Boolean) {
        center.removeAll()
        center.add(if (empty) emptyPanel else treeScroll, BorderLayout.CENTER)
        center.revalidate()
        center.repaint()
    }

    private fun openExampleFile() {
        val file = resolveExampleFile() ?: materializeBundledExample()
        if (file == null) {
            Messages.showWarningDialog(
                project,
                "Could not find or create examples/refund-request.bpmn.",
                "BPMN",
            )
            return
        }
        FileEditorManager.getInstance(project).openFile(file, true)
        reload()
    }

    private fun resolveExampleFile(): VirtualFile? {
        val base = project.guessProjectDir()
        val candidates = mutableListOf<String>()
        if (base != null) {
            candidates += listOf(
                "${base.path}/examples/refund-request.bpmn",
                "${base.path}/refund-request.bpmn",
            )
        }
        candidates += "/workspace/forge-bpmn-intellij/examples/refund-request.bpmn"
        val lfs = LocalFileSystem.getInstance()
        for (path in candidates) {
            val vf = lfs.refreshAndFindFileByPath(path)
            if (vf != null && vf.exists()) return vf
        }
        return collectBpmnFiles().firstOrNull { it.name.equals("refund-request.bpmn", ignoreCase = true) }
    }

    /** Copy bundled sample into the project so Marketplace installs still get a CTA. */
    private fun materializeBundledExample(): VirtualFile? {
        val base = project.guessProjectDir() ?: return null
        val stream = javaClass.getResourceAsStream("/examples/refund-request.bpmn") ?: return null
        return ApplicationManager.getApplication().runWriteAction<VirtualFile?> {
            val examplesDir = base.findChild("examples")
                ?: base.createChildDirectory(this, "examples")
            val existing = examplesDir.findChild("refund-request.bpmn")
            if (existing != null) return@runWriteAction existing
            val created = examplesDir.createChildData(this, "refund-request.bpmn")
            stream.use { VfsUtil.saveText(created, it.reader().readText()) }
            created
        }
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
                showEmpty(files.isEmpty())
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
}
