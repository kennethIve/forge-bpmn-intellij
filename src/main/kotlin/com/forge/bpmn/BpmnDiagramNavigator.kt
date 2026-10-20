package com.forge.bpmn

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object BpmnDiagramNavigator {
    fun select(project: Project, file: VirtualFile, elementId: String?) {
        if (elementId.isNullOrBlank() || !file.isValid) return
        OpenFileDescriptor(project, file).navigate(true)
        FileEditorManager.getInstance(project).setSelectedEditor(file, "forge-bpmn-diagram")
        for (editor in FileEditorManager.getInstance(project).getEditors(file)) {
            if (editor is BpmnFileEditor) {
                editor.selectElement(elementId)
                return
            }
        }
    }
}
