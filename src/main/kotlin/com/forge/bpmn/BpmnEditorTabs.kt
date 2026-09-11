package com.forge.bpmn

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

object BpmnEditorTabs {
    const val DIAGRAM_ID = "forge-bpmn-diagram"
    const val XML_ID = "forge-bpmn-xml"

    fun bar(project: Project, file: VirtualFile, current: String): JComponent {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
        bar.border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)

        val bpmn = JButton("BPMN")
        val xml = JButton("XML")
        bpmn.toolTipText = "Switch to the BPMN diagram editor"
        xml.toolTipText = "Switch to XML source"
        bpmn.isFocusable = false
        xml.isFocusable = false
        bpmn.margin = JBUI.insets(2, 10)
        xml.margin = JBUI.insets(2, 10)
        bpmn.isEnabled = current != "bpmn"
        xml.isEnabled = current != "xml"
        bpmn.addActionListener {
            FileEditorManager.getInstance(project).setSelectedEditor(file, DIAGRAM_ID)
        }
        xml.addActionListener {
            FileEditorManager.getInstance(project).setSelectedEditor(file, XML_ID)
        }

        bar.add(bpmn)
        bar.add(xml)
        return bar
    }
}
