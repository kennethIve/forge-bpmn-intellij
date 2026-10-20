package com.forge.bpmn

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class BpmnSelectOnDiagramFix(private val elementId: String?) : LocalQuickFix, IntentionAction {
    override fun getText(): String = "Select on diagram"
    override fun getFamilyName(): String = "Select on diagram"
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        !elementId.isNullOrBlank()

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val vf = file?.virtualFile ?: return
        BpmnDiagramNavigator.select(project, vf, elementId)
    }

    override fun startInWriteAction(): Boolean = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile?.virtualFile ?: return
        BpmnDiagramNavigator.select(project, file, elementId)
    }
}
