package com.forge.bpmn

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Color
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class BpmnFileEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {
    private val panel = JPanel(BorderLayout())
    private var browser: JBCefBrowser? = null
    private var applyingFromJs = false
    private var loaded = false
    private var diagramActive = true
    private val themeConnection = project.messageBus.connect()
    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (!applyingFromJs && loaded && diagramActive) {
                SwingUtilities.invokeLater { pushXml() }
            }
        }
    }

    init {
        panel.background = JBColor.PanelBackground
        if (!JBCefApp.isSupported()) {
            panel.add(JLabel("JCEF is required for the BPMN diagram editor.", SwingConstants.CENTER), BorderLayout.CENTER)
        } else {
            val b = JBCefBrowser()
            browser = b
            b.component.background = JBColor.PanelBackground
            val query = JBCefJSQuery.create(b as JBCefBrowserBase)
            query.addHandler { xml ->
                applyingFromJs = true
                try {
                    WriteCommandAction.runWriteCommandAction(project) {
                        val doc = FileDocumentManager.getInstance().getDocument(file)
                        if (doc != null) {
                            doc.setText(xml)
                        } else {
                            file.setBinaryContent(xml.toByteArray(Charsets.UTF_8))
                        }
                    }
                } finally {
                    applyingFromJs = false
                }
                null
            }
            b.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(br: CefBrowser?, frame: CefFrame?, status: Int) {
                    if (frame == null || !frame.isMain) return
                    loaded = true
                    val inject = "window.__onXmlChange = function(xml) { " + query.inject("xml") + " };"
                    br?.executeJavaScript(inject, br.url, 0)
                    SwingUtilities.invokeLater {
                        applyTheme()
                        pushXml()
                    }
                }
            }, b.cefBrowser)
            FileDocumentManager.getInstance().getDocument(file)?.addDocumentListener(documentListener)
            b.loadURL(BpmnAssets.root.resolve("index.html").toUri().toString())
            panel.add(b.component, BorderLayout.CENTER)
            panel.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    if (loaded) SwingUtilities.invokeLater { fitViewport() }
                }
            })
            themeConnection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
                SwingUtilities.invokeLater { applyTheme() }
            })
        }
    }

    private fun jsString(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                Char(92) -> { sb.append(Char(92)); sb.append(Char(92)) }
                '"' -> { sb.append(Char(92)); sb.append('"') }
                Char(10) -> { sb.append(Char(92)); sb.append('n') }
                Char(13) -> { }
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun pushXml() {
        val b = browser ?: return
        if (!loaded) return
        val xml = String(file.contentsToByteArray(), Charsets.UTF_8)
        b.cefBrowser.executeJavaScript(
            "window.__loadXml && window.__loadXml(" + jsString(xml) + ");",
            b.cefBrowser.url,
            0,
        )
    }

    private fun fitViewport() {
        val b = browser ?: return
        if (!loaded) return
        b.cefBrowser.executeJavaScript(
            "window.__fit && window.__fit();",
            b.cefBrowser.url,
            0,
        )
    }

    private fun hex(c: Color): String {
        return String.format("#%02x%02x%02x", c.red, c.green, c.blue)
    }

    private fun applyTheme() {
        val b = browser ?: return
        if (!loaded) return
        val dark = !JBColor.isBright()
        val scheme = EditorColorsManager.getInstance().globalScheme
        val canvas = hex(scheme.defaultBackground)
        val fg = hex(scheme.defaultForeground)
        val panelBg = hex(JBColor.namedColor("Panel.background", JBColor.PanelBackground))
        val border = hex(JBColor.namedColor("Borders.color", JBColor.border()))
        val muted = hex(JBColor.namedColor("Label.infoForeground", JBColor.GRAY))
        val input = hex(JBColor.namedColor("TextField.background", scheme.defaultBackground))
        val hover = hex(JBColor.namedColor("ActionButton.hoverBackground", JBColor.GRAY))
        val accent = hex(JBColor.namedColor("Link.activeForeground", JBColor.BLUE))
        panel.background = JBColor.PanelBackground
        b.component.background = JBColor.PanelBackground
        b.cefBrowser.executeJavaScript(
            "window.__applyTheme && window.__applyTheme({dark:" + dark +
                ",bg:" + jsString(panelBg) +
                ",canvas:" + jsString(canvas) +
                ",panel:" + jsString(panelBg) +
                ",fg:" + jsString(fg) +
                ",muted:" + jsString(muted) +
                ",border:" + jsString(border) +
                ",input:" + jsString(input) +
                ",hover:" + jsString(hover) +
                ",accent:" + jsString(accent) +
                "});",
            b.cefBrowser.url,
            0,
        )
    }

    fun setDiagramActive(value: Boolean) {
        diagramActive = value
        if (value) SwingUtilities.invokeLater { pushXml() }
    }

    fun reloadFromFile() {
        pushXml()
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = panel
    override fun getName(): String = "BPMN"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file
    override fun dispose() {
        themeConnection.disconnect()
        FileDocumentManager.getInstance().getDocument(file)?.removeDocumentListener(documentListener)
        browser?.let { Disposer.dispose(it) }
    }
}
