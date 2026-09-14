package com.forge.bpmn

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
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
import com.intellij.ui.components.Magnificator
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.MouseInfo
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import java.lang.reflect.Proxy
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.pow

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
    private var zoomTimer: Timer? = null
    private var cefZoomResetting = false
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
            Disposer.register(b, query)
            query.addHandler { payload ->
                val xml = decodeXmlPayload(payload)
                onEdt {
                    writeXmlFromJs(xml)
                }
                null
            }
            b.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(br: CefBrowser?, frame: CefFrame?, status: Int) {
                    if (frame == null || !frame.isMain) return
                    loaded = true
                    val inject = "window.__onXmlChange = function(xml) { " +
                        query.inject("JSON.stringify(xml)") +
                        " };"
                    br?.executeJavaScript(inject, br.url, 0)
                    setCefZoomLevel(b, 0.0)
                    SwingUtilities.invokeLater {
                        applyTheme()
                        pushXml()
                        attachCanvasZoom(b)
                    }
                }
            }, b.cefBrowser)
            FileDocumentManager.getInstance().getDocument(file)?.addDocumentListener(documentListener)
            b.loadURL(BpmnAssets.root.resolve("index.html").toUri().toString())
            panel.add(b.component, BorderLayout.CENTER)
            panel.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    if (loaded) SwingUtilities.invokeLater { notifyResized() }
                }
            })
            themeConnection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
                SwingUtilities.invokeLater { applyTheme() }
            })
        }
    }

    private fun onEdt(block: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) block() else app.invokeAndWait(block)
    }

    private fun decodeXmlPayload(payload: String): String {
        val trimmed = payload.trim()
        if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length - 1)
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
        }
        return payload
    }

    private fun writeXmlFromJs(xml: String) {
        if (!file.isValid) return
        applyingFromJs = true
        try {
            val manager = FileDocumentManager.getInstance()
            val doc = manager.getDocument(file)
            val app = ApplicationManager.getApplication()
            if (doc != null) {
                if (doc.text != xml) {
                    CommandProcessor.getInstance().runUndoTransparentAction {
                        app.runWriteAction { doc.setText(xml) }
                    }
                }
                if (manager.isFileModified(file)) {
                    manager.saveDocument(doc)
                }
            } else {
                val bytes = xml.toByteArray(Charsets.UTF_8)
                if (!file.contentsToByteArray().contentEquals(bytes)) {
                    app.runWriteAction { file.setBinaryContent(bytes) }
                }
            }
        } finally {
            applyingFromJs = false
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

    private fun notifyResized() {
        val b = browser ?: return
        if (!loaded) return
        b.cefBrowser.executeJavaScript(
            "window.__resized && window.__resized();",
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
    override fun isModified(): Boolean = FileDocumentManager.getInstance().isFileModified(file)
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile = file
    override fun dispose() {
        zoomTimer?.stop()
        zoomTimer = null
        themeConnection.disconnect()
        FileDocumentManager.getInstance().getDocument(file)?.removeDocumentListener(documentListener)
        browser?.let { Disposer.dispose(it) }
    }

    private fun attachCanvasZoom(b: JBCefBrowser) {
        val mag = Magnificator { scale, at ->
            jsZoomBy(b, scale, at.x.toDouble(), at.y.toDouble())
            at
        }
        panel.putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, mag)
        b.component.putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, mag)
        val ui = browserUi(b)
        if (ui is JComponent) {
            ui.putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, mag)
            installAppleMagnify(ui, b)
        }
        installAppleMagnify(panel, b)
        if (zoomTimer == null) {
            val timer = Timer(16) { redirectCefZoom(b) }
            timer.isRepeats = true
            zoomTimer = timer
        }
        zoomTimer?.start()
        setCefZoomLevel(b, 0.0)
    }

    private fun browserUi(b: JBCefBrowser): Component {
        try {
            val method = b.javaClass.methods.find { it.name == "getBrowserComponent" && it.parameterCount == 0 }
            val component = method?.invoke(b)
            if (component is Component) return component
        } catch (_: Throwable) {
        }
        try {
            val ui = b.cefBrowser.uiComponent
            if (ui is Component) return ui
        } catch (_: Throwable) {
        }
        return b.component
    }

    private fun redirectCefZoom(b: JBCefBrowser) {
        if (!loaded) return
        val level = cefZoomLevel(b)
        if (cefZoomResetting) {
            if (abs(level) < 0.0001) {
                cefZoomResetting = false
            } else {
                setCefZoomLevel(b, 0.0)
            }
            return
        }
        if (abs(level) < 0.0001) return
        val factor = 1.2.pow(level)
        cefZoomResetting = true
        setCefZoomLevel(b, 0.0)
        jsZoomBy(b, factor, Double.NaN, Double.NaN)
    }

    private fun cefZoomLevel(b: JBCefBrowser): Double {
        try {
            val method = b.javaClass.methods.find { it.name == "getZoomLevel" && it.parameterCount == 0 }
            val value = method?.invoke(b)
            if (value is Number) return value.toDouble()
        } catch (_: Throwable) {
        }
        try {
            val method = b.cefBrowser.javaClass.methods.find { it.name == "getZoomLevel" && it.parameterCount == 0 }
            val value = method?.invoke(b.cefBrowser)
            if (value is Number) return value.toDouble()
        } catch (_: Throwable) {
        }
        return 0.0
    }

    private fun setCefZoomLevel(b: JBCefBrowser, level: Double) {
        try {
            val method = b.javaClass.methods.find { it.name == "setZoomLevel" && it.parameterCount == 1 }
            if (method != null) {
                method.invoke(b, level)
                return
            }
        } catch (_: Throwable) {
        }
        try {
            b.cefBrowser.javaClass.getMethod("setZoomLevel", java.lang.Double.TYPE).invoke(b.cefBrowser, level)
        } catch (_: Throwable) {
        }
    }

    private fun jsZoomBy(b: JBCefBrowser, factor: Double, x: Double, y: Double) {
        if (!loaded || !factor.isFinite() || abs(factor - 1.0) < 0.0001) return
        val sx = if (x.isFinite()) x.toString() else "undefined"
        val sy = if (y.isFinite()) y.toString() else "undefined"
        b.cefBrowser.executeJavaScript(
            "window.__zoomBy && window.__zoomBy(" + factor + "," + sx + "," + sy + ");",
            b.cefBrowser.url,
            0,
        )
    }

    private fun installAppleMagnify(target: JComponent, b: JBCefBrowser) {
        try {
            val gestureUtilities = Class.forName("com.apple.eawt.event.GestureUtilities")
            val listenerClass = Class.forName("com.apple.eawt.event.MagnificationListener")
            val eventClass = Class.forName("com.apple.eawt.event.MagnificationEvent")
            val getMagnification = eventClass.getMethod("getMagnification")
            val listener = Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass)) { _, method, args ->
                if (method.name == "magnify" && !args.isNullOrEmpty()) {
                    val mag = (getMagnification.invoke(args[0]) as Number).toDouble()
                    val factor = 1.0 + mag
                    val at = pointerIn(target)
                    jsZoomBy(b, factor, at.x.toDouble(), at.y.toDouble())
                }
                null
            }
            val gestureListener = Class.forName("com.apple.eawt.event.GestureListener")
            gestureUtilities.getMethod("addGestureListenerTo", JComponent::class.java, gestureListener)
                .invoke(null, target, listener)
        } catch (_: Throwable) {
        }
    }

    private fun pointerIn(target: Component): Point {
        return try {
            val loc = MouseInfo.getPointerInfo()?.location ?: return Point(target.width / 2, target.height / 2)
            SwingUtilities.convertPointFromScreen(loc, target)
            loc
        } catch (_: Throwable) {
            Point(target.width / 2, target.height / 2)
        }
    }
}
