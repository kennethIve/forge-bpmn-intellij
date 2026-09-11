package com.forge.bpmn

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class BpmnXmlEditor(
    project: Project,
    private val file: VirtualFile,
    private val delegate: TextEditor,
) : TextEditor by delegate {
    private val root = JPanel(BorderLayout())

    init {
        root.add(delegate.component, BorderLayout.CENTER)
        root.add(BpmnEditorTabs.bar(project, file, "xml"), BorderLayout.SOUTH)
    }

    override fun getName(): String = "XML"
    override fun getFile(): VirtualFile = file
    override fun getComponent(): JComponent = root
    override fun getPreferredFocusedComponent(): JComponent {
        return delegate.preferredFocusedComponent ?: delegate.component
    }
    override fun dispose() {
        delegate.dispose()
    }
}
