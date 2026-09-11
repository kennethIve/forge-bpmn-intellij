package com.forge.bpmn

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile

class BpmnXmlEditor(
    private val file: VirtualFile,
    private val delegate: TextEditor,
) : TextEditor by delegate {
    override fun getName(): String = "XML"
    override fun getFile(): VirtualFile = file
}
