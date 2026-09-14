package com.forge.bpmn

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

object BpmnLint {
    data class Issue(
        val id: String,
        val elementId: String?,
        val level: String,
        val rule: String,
        val message: String,
    )

    private val tasks = setOf(
        "task", "userTask", "serviceTask", "sendTask", "receiveTask",
        "scriptTask", "businessRuleTask", "manualTask", "callActivity",
    )
    private val nodes = tasks + setOf(
        "startEvent", "endEvent", "intermediateThrowEvent", "intermediateCatchEvent",
        "boundaryEvent", "exclusiveGateway", "parallelGateway", "inclusiveGateway",
        "eventBasedGateway", "complexGateway", "subProcess",
    )

    fun check(xml: String): List<Issue> {
        val trimmed = xml.trim()
        if (trimmed.isEmpty()) return listOf(Issue("empty", null, "error", "xml", "Diagram is empty."))
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val doc = try {
            factory.newDocumentBuilder().parse(InputSource(StringReader(trimmed)))
        } catch (_: Exception) {
            return listOf(Issue("xml-parse", null, "error", "xml", "BPMN XML is not well-formed."))
        }
        val definitions = locals(doc.documentElement, "definitions").firstOrNull()
            ?: if (doc.documentElement.localName == "definitions") doc.documentElement else null
        if (definitions == null) return listOf(Issue("no-definitions", null, "error", "schema", "Missing bpmn:definitions root."))
        val processes = locals(definitions, "process")
        if (processes.isEmpty()) return listOf(Issue("no-process", null, "error", "schema", "No bpmn:process in this diagram."))
        val issues = mutableListOf<Issue>()
        val ids = mutableMapOf<String, Int>()
        val all = definitions.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val el = all.item(i) as Element
            val id = attr(el, "id")
            if (id.isNotEmpty()) ids[id] = (ids[id] ?: 0) + 1
        }
        for ((id, count) in ids) {
            if (count > 1) issues += Issue("dup-$id", id, "error", "duplicate-id", "Duplicate id $id.")
        }
        for (process in processes) {
            val pid = attr(process, "id").ifEmpty { "process" }
            if (locals(process, "startEvent").isEmpty()) {
                issues += Issue("no-start-$pid", pid, "error", "start-event", "Process has no start event.")
            }
            if (locals(process, "endEvent").isEmpty()) {
                issues += Issue("no-end-$pid", pid, "error", "end-event", "Process has no end event.")
            }
            val descendants = process.getElementsByTagName("*")
            for (i in 0 until descendants.length) {
                val el = descendants.item(i) as Element
                if (!isBpmn(el)) continue
                if (el.localName in tasks && attr(el, "name").isBlank()) {
                    val tid = attr(el, "id")
                    issues += Issue("unnamed-$tid", tid, "warning", "task-name", "${el.localName} should have a name.")
                }
            }
            val defaults = mutableSetOf<String>()
            for (i in 0 until descendants.length) {
                val d = attr(descendants.item(i) as Element, "default")
                if (d.isNotEmpty()) defaults += d
            }
            val inMap = mutableMapOf<String, Int>()
            val outMap = mutableMapOf<String, Int>()
            for (flow in locals(process, "sequenceFlow")) {
                val src = attr(flow, "sourceRef")
                val tgt = attr(flow, "targetRef")
                if (src.isNotEmpty()) outMap[src] = (outMap[src] ?: 0) + 1
                if (tgt.isNotEmpty()) inMap[tgt] = (inMap[tgt] ?: 0) + 1
            }
            for (gw in locals(process, "exclusiveGateway")) {
                val gid = attr(gw, "id")
                val outgoingFlows = locals(process, "sequenceFlow").filter { attr(it, "sourceRef") == gid }
                if (outgoingFlows.size < 2) {
                    issues += Issue("xor-out-$gid", gid, "warning", "gateway", "Exclusive gateway should have at least two outgoing flows.")
                }
                for (flow in outgoingFlows) {
                    val flowId = attr(flow, "id")
                    val named = attr(flow, "name").isNotBlank()
                    val condition = locals(flow, "conditionExpression").firstOrNull()?.textContent?.trim().orEmpty()
                    if (!named && condition.isEmpty() && flowId !in defaults) {
                        issues += Issue("xor-cond-$flowId", flowId, "warning", "flow-condition", "Outgoing flow from exclusive gateway needs a name, condition, or default.")
                    }
                }
            }
            for (flow in locals(process, "sequenceFlow")) {
                val fid = attr(flow, "id")
                if (attr(flow, "sourceRef").isEmpty() || attr(flow, "targetRef").isEmpty()) {
                    issues += Issue("flow-ends-$fid", fid, "error", "sequence-flow", "Sequence flow is missing source or target.")
                }
                val cond = locals(flow, "conditionExpression").firstOrNull()?.textContent?.trim().orEmpty()
                if (cond.isNotEmpty() && (cond.contains("$" + "{") || !cond.startsWith("="))) {
                    issues += Issue("feel-$fid", fid, "warning", "camunda8-feel", "Camunda 8 conditions must be FEEL and start with =.")
                }
            }
            for (i in 0 until descendants.length) {
                val node = descendants.item(i) as Element
                if (node.localName !in nodes || node.localName == "boundaryEvent" || !isBpmn(node)) continue
                val nid = attr(node, "id")
                val incoming = inMap[nid] ?: 0
                val outgoing = outMap[nid] ?: 0
                when (node.localName) {
                    "startEvent" -> if (outgoing == 0) issues += Issue("start-out-$nid", nid, "error", "disconnected", "Start event has no outgoing flow.")
                    "endEvent" -> if (incoming == 0) issues += Issue("end-in-$nid", nid, "error", "disconnected", "End event has no incoming flow.")
                    else -> {
                        if (incoming == 0 && outgoing == 0) {
                            issues += Issue("orphan-$nid", nid, "warning", "disconnected", "${node.localName} is not connected.")
                        } else if (incoming == 0) {
                            issues += Issue("no-in-$nid", nid, "warning", "disconnected", "${node.localName} has no incoming flow.")
                        }
                    }
                }
            }
            for (i in 0 until descendants.length) {
                val el = descendants.item(i) as Element
                if (!isBpmn(el)) continue
                val nid = attr(el, "id")
                val job = zeebeChild(el, "taskDefinition")?.let { attr(it, "type") }.orEmpty()
                when (el.localName) {
                    "serviceTask", "sendTask" -> if (job.isBlank()) {
                        issues += Issue("job-$nid", nid, "error", "camunda8-job-type", "Camunda 8 ${el.localName} needs a zeebe:taskDefinition type (job worker).")
                    }
                    "callActivity" -> {
                        val called = zeebeChild(el, "calledElement")?.let { attr(it, "processId") }.orEmpty()
                        if (called.isBlank()) {
                            issues += Issue("called-$nid", nid, "error", "camunda8-called-element", "Call activity needs zeebe:calledElement processId.")
                        }
                    }
                    "scriptTask" -> {
                        if (zeebeChild(el, "script") == null && job.isBlank()) {
                            issues += Issue("script-$nid", nid, "error", "camunda8-script", "Script task needs zeebe:script or a job type.")
                        }
                    }
                    "businessRuleTask" -> {
                        val decision = zeebeChild(el, "calledDecision")?.let { attr(it, "decisionId") }.orEmpty()
                        if (decision.isBlank() && job.isBlank()) {
                            issues += Issue("decision-$nid", nid, "error", "camunda8-decision", "Business rule task needs a called decision id or job type.")
                        }
                    }
                    "userTask" -> if (zeebeChild(el, "userTask") == null) {
                        issues += Issue("usertask-$nid", nid, "warning", "camunda8-user-task", "Enable Camunda user task (zeebe:userTask).")
                    }
                }
                val c7 = camunda7Impl(el)
                if (c7.isNotEmpty()) {
                    issues += Issue("c7-$nid", nid, "error", "camunda7-legacy", "Camunda 7 property camunda:$c7 is not supported on Camunda 8.")
                }
            }
        }
        return issues
    }

    private fun attr(el: Element, name: String): String = el.getAttribute(name)

    private fun isBpmn(el: Element): Boolean {
        val ns = el.namespaceURI
        return ns.isNullOrEmpty() || ns == "http://www.omg.org/spec/BPMN/20100524/MODEL"
    }

    private fun zeebeChild(el: Element, name: String): Element? {
        val ext = locals(el, "extensionElements").firstOrNull() ?: return null
        return locals(ext, name).firstOrNull()
    }

    private fun camunda7Impl(el: Element): String {
        val ns = "http://camunda.org/schema/1.0/bpmn"
        val names = listOf("class", "delegateExpression", "expression", "topic")
        for (name in names) {
            val namespaced = el.getAttributeNS(ns, name)
            if (!namespaced.isNullOrEmpty()) return name
            val prefixed = el.getAttribute("camunda:" + name)
            if (prefixed.isNotEmpty()) return name
        }
        return ""
    }

    private fun locals(root: Node, name: String): List<Element> {
        val out = mutableListOf<Element>()
        val start = if (root is Element) root else return out
        if (start.localName == name) out += start
        val list = start.getElementsByTagName("*")
        for (i in 0 until list.length) {
            val el = list.item(i) as Element
            if (el.localName == name) out += el
        }
        return out
    }
}
