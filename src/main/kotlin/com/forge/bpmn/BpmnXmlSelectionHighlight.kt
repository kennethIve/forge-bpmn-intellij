package com.forge.bpmn

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color
import java.awt.Font

/**
 * XML-side selection sync highlight: solid product accent `#3574F0` background
 * + white text (opaque, no underline / no semi-transparent stack). Same in
 * Light and Dark. One highlighter per editor.
 */
object BpmnXmlSelectionHighlight {
    private val HIGHLIGHTER_KEY = Key.create<RangeHighlighter>("forge.bpmn.xml.selection.hl")
    /** UX-locked selection blue — same token as diagram accent / icons. */
    val ACCENT: Color = Color.decode("#3574F0")
    private val FG: Color = Color.WHITE

    fun clear(editor: Editor) {
        val existing = editor.getUserData(HIGHLIGHTER_KEY) ?: return
        editor.putUserData(HIGHLIGHTER_KEY, null)
        try {
            editor.markupModel.removeHighlighter(existing)
        } catch (_: Exception) {
            // Highlighter may already be disposed with the editor.
        }
    }

    fun clearForFile(file: VirtualFile, fem: FileEditorManager) {
        for (editor in fem.getEditors(file)) {
            if (editor is TextEditor) clear(editor.editor)
        }
    }

    /**
     * Apply solid `#3574F0` + white text over `[start, end)`. Replaces any prior
     * sync highlight on this editor.
     */
    fun apply(editor: Editor, start: Int, end: Int) {
        if (start < 0 || end <= start || end > editor.document.textLength) {
            clear(editor)
            return
        }
        clear(editor)
        val attrs = TextAttributes(FG, ACCENT, null, null, Font.PLAIN)
        val hl = editor.markupModel.addRangeHighlighter(
            start,
            end,
            HighlighterLayer.SELECTION - 1,
            attrs,
            HighlighterTargetArea.EXACT_RANGE,
        )
        editor.putUserData(HIGHLIGHTER_KEY, hl)
    }

    fun xmlEditors(file: VirtualFile, fem: FileEditorManager): List<Editor> {
        val out = ArrayList<Editor>(2)
        for (editor in fem.getEditors(file)) {
            if (editor is TextEditor && editor.getName() == "XML") {
                out.add(editor.editor)
            }
        }
        if (out.isEmpty()) {
            for (editor in fem.getEditors(file)) {
                if (editor is TextEditor) out.add(editor.editor)
            }
        }
        return out
    }

    fun moveCaretQuiet(editor: Editor, offset: Int, scroll: Boolean) {
        val len = editor.document.textLength
        if (offset < 0 || offset > len) return
        editor.caretModel.moveToOffset(offset)
        editor.selectionModel.removeSelection()
        if (scroll) {
            editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        }
    }
}
