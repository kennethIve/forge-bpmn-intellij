package com.forge.bpmn

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlFile

class BpmnInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : XmlElementVisitor() {
            override fun visitXmlFile(file: XmlFile) {
                if (!"bpmn".equals(file.virtualFile?.extension, ignoreCase = true)) return
                for (issue in BpmnLint.check(file.text)) {
                    holder.registerProblem(file, "[${issue.rule}] ${issue.message}")
                }
            }
        }
    }
}
