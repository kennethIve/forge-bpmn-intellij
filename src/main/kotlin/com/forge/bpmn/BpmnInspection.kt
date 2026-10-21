package com.forge.bpmn

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * Batch-style BPMN lint for [LocalInspectionTool] / Inspect Code.
 * Not registered in plugin.xml while [BpmnAnnotator] owns the live Problems feed
 * (Annotator works without modules; registering both duplicates rows).
 */
class BpmnInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "BPMN syntax"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : XmlElementVisitor() {
            override fun visitXmlFile(file: XmlFile) {
                if (!"bpmn".equals(file.virtualFile?.extension, ignoreCase = true)) return
                val root = file.rootTag ?: return
                for (issue in BpmnLint.check(file.text)) {
                    val target: XmlAttribute? = issue.elementId?.let { findId(root, it) }
                    val psi = target ?: root
                    val highlight = if (issue.level == "error") {
                        ProblemHighlightType.ERROR
                    } else {
                        ProblemHighlightType.WARNING
                    }
                    val message = issue.message
                    if (issue.elementId.isNullOrBlank()) {
                        holder.registerProblem(psi, message, highlight)
                    } else {
                        holder.registerProblem(
                            psi,
                            message,
                            highlight,
                            BpmnSelectOnDiagramFix(issue.elementId),
                        )
                    }
                }
            }
        }
    }

    private fun findId(root: XmlTag, id: String): XmlAttribute? {
        val stack = ArrayDeque<XmlTag>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val t = stack.removeFirst()
            val attr = t.getAttribute("id")
            if (attr?.value == id) return attr
            stack.addAll(t.subTags)
        }
        return null
    }
}
