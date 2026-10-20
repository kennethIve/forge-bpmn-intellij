package com.forge.bpmn

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * In-editor XML underlines for BPMN lint. Problems tool window is fed primarily by
 * [BpmnInspection] (with Select-on-diagram fix); Annotator stays for immediate gutter/underline
 * while typing. Severity matches Inspection so messages stay consistent.
 */
class BpmnAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val tag = element as? XmlTag ?: return
        if (tag.localName != "definitions" || tag.parentTag != null) return
        val file = element.containingFile as? XmlFile ?: return
        if (!"bpmn".equals(file.virtualFile?.extension, ignoreCase = true)) return
        val issues = BpmnLint.check(file.text)
        for (issue in issues) {
            val severity = if (issue.level == "error") HighlightSeverity.ERROR else HighlightSeverity.WARNING
            val target = issue.elementId?.let { findId(tag, it) } ?: tag
            val builder = holder.newAnnotation(severity, issue.message)
                .range(target.textRange)
            if (!issue.elementId.isNullOrBlank()) {
                builder.withFix(BpmnSelectOnDiagramFix(issue.elementId))
            }
            builder.create()
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
