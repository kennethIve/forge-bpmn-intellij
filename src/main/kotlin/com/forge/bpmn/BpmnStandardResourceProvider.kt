package com.forge.bpmn

import com.intellij.javaee.ResourceRegistrar
import com.intellij.javaee.StandardResourceProvider

/**
 * BPMN / Zeebe / modeler xmlns values are valid, but IntelliJ has no schema catalog
 * entry for them. Ignoring those URIs clears "URI is not registered" noise without
 * introducing XSD validation errors on Camunda extensions.
 */
class BpmnStandardResourceProvider : StandardResourceProvider {
    override fun registerResources(registrar: ResourceRegistrar) {
        URLS.forEach { registrar.addIgnoredResource(it) }
    }

    companion object {
        val URLS = listOf(
            "http://www.omg.org/spec/BPMN/20100524/MODEL",
            "http://www.omg.org/spec/BPMN/20100524/DI",
            "http://www.omg.org/spec/DD/20100524/DC",
            "http://www.omg.org/spec/DD/20100524/DI",
            "http://camunda.org/schema/zeebe/1.0",
            "http://camunda.org/schema/modeler/1.0",
        )
    }
}
