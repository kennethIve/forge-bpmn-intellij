package com.forge.bpmn

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class BpmnFileType : LanguageFileType(XMLLanguage.INSTANCE) {
    override fun getName(): String = "BPMN"
    override fun getDescription(): String = "BPMN 2.0 diagram"
    override fun getDefaultExtension(): String = "bpmn"
    override fun getIcon(): Icon = BpmnIcons.FILE

    companion object {
        @JvmField
        val INSTANCE = BpmnFileType()
    }
}
