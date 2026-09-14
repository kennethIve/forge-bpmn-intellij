package com.forge.bpmn

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object BpmnAssets {
    private val names = listOf(
        "index.html",
        "camunda-cloud-modeler.production.min.js",
        "assets/camunda-cloud-modeler.css",
        "assets/base-modeler.css",
        "assets/diagram-js.css",
        "assets/bpmn-js.css",
        "assets/diagram-js-minimap.css",
        "assets/properties-panel.css",
        "assets/color-picker.css",
        "assets/element-templates.css",
        "assets/element-template-chooser.css",
        "assets/popup-menu.css",
        "assets/bpmn-js-token-simulation.css",
        "assets/bpmn-font/css/bpmn-embedded.css",
        "assets/bpmn-font/font/bpmn.woff2",
        "assets/bpmn-font/font/bpmn.woff",
        "assets/bpmn-font/font/bpmn.ttf",
        "assets/bpmn-font/font/bpmn.eot",
        "assets/bpmn-font/font/bpmn.svg"
    )

    val root: Path by lazy {
        val dir = Files.createTempDirectory("forge-bpmn-")
        dir.toFile().deleteOnExit()
        for (name in names) {
            val stream = BpmnAssets::class.java.getResourceAsStream("/bpmn-editor/$name") ?: continue
            stream.use {
                val target = dir.resolve(name)
                Files.createDirectories(target.parent)
                Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        dir
    }
}
