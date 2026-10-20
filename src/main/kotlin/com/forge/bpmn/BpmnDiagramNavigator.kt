package com.forge.bpmn

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object BpmnDiagramNavigator {
    fun select(project: Project, file: VirtualFile, elementId: String?) {
        if (elementId.isNullOrBlank() || !file.isValid || project.isDisposed) return
        // Defer off the Problems tree selection / caret callback to avoid nested
        // navigate→tab-switch races that surface as a generic "IDE error occurred" toast.
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed || !file.isValid) return@invokeLater
            try {
                val fem = FileEditorManager.getInstance(project)
                if (!fem.isFileOpen(file)) {
                    OpenFileDescriptor(project, file).navigate(true)
                }
                fem.setSelectedEditor(file, "forge-bpmn-diagram")
                for (editor in fem.getEditors(file)) {
                    if (editor is BpmnFileEditor) {
                        editor.selectElement(elementId)
                        return@invokeLater
                    }
                }
            } catch (_: Exception) {
                // Swallow tab/CEF races; selection is best-effort.
            }
        }, ModalityState.defaultModalityState(), project.disposed)
    }
}
