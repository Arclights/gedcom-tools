package com.arclights.tui

import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractInteractableComponent
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.InteractableRenderer
import com.googlecode.lanterna.gui2.TextGUIGraphics
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType

/** A run of text sharing a single foreground color (null = terminal default). */
private data class AnsiSegment(val text: String, val color: TextColor?)

/**
 * Parses a string containing the SGR escape sequences emitted by [com.arclights.Color] into lines of
 * colored segments. Only the two forms we produce are understood: a reset (`ESC[0m`) and a 256-color
 * foreground (`ESC[38:5:Nm` / `ESC[38;5;Nm`); anything else leaves the color unchanged.
 */
private fun parseAnsi(content: String): List<List<AnsiSegment>> {
    val lines = mutableListOf(mutableListOf<AnsiSegment>())
    var color: TextColor? = null

    fun emit(text: String) {
        text.split("\n").forEachIndexed { i, part ->
            if (i > 0) lines.add(mutableListOf())
            if (part.isNotEmpty()) lines.last().add(AnsiSegment(part, color))
        }
    }

    var index = 0
    for (match in ESCAPE.findAll(content)) {
        if (match.range.first > index) emit(content.substring(index, match.range.first))
        color = colorForCode(match.groupValues[1], color)
        index = match.range.last + 1
    }
    if (index < content.length) emit(content.substring(index))

    return lines
}

private val ESCAPE = Regex("\\[([0-9;:]*)m")

private fun colorForCode(code: String, current: TextColor?): TextColor? {
    if (code.isEmpty() || code == "0") return null // reset
    val parts = code.split(':', ';')
    val at = parts.indexOf("38")
    if (at >= 0 && parts.getOrNull(at + 1) == "5") {
        parts.getOrNull(at + 2)?.toIntOrNull()?.let { return TextColor.Indexed(it) }
    }
    return current
}

/**
 * A focusable, vertically scrollable, read-only view of colored text. Arrow keys / PageUp / PageDown /
 * Home / End scroll; Escape invokes [onClose] (used to dismiss the enclosing window). Unhandled keys
 * (Tab, etc.) fall through to the default focus handling so the user can reach the Close button.
 */
class AnsiTextView(content: String, private val onClose: () -> Unit) :
    AbstractInteractableComponent<AnsiTextView>() {

    private val lines = parseAnsi(content)
    private val contentWidth = lines.maxOfOrNull { line -> line.sumOf { it.text.length } } ?: 0
    private var scroll = 0

    private fun viewportRows() = size?.rows ?: 1
    private fun maxScroll() = maxOf(0, lines.size - viewportRows())

    override fun handleKeyStroke(keyStroke: KeyStroke): Interactable.Result {
        when (keyStroke.keyType) {
            KeyType.ArrowDown -> scroll = minOf(scroll + 1, maxScroll())
            KeyType.ArrowUp -> scroll = maxOf(scroll - 1, 0)
            KeyType.PageDown -> scroll = minOf(scroll + viewportRows(), maxScroll())
            KeyType.PageUp -> scroll = maxOf(scroll - viewportRows(), 0)
            KeyType.Home -> scroll = 0
            KeyType.End -> scroll = maxScroll()
            KeyType.Escape -> onClose()
            else -> return super.handleKeyStroke(keyStroke)
        }
        return Interactable.Result.HANDLED
    }

    override fun createDefaultRenderer() = object : InteractableRenderer<AnsiTextView> {
        override fun getPreferredSize(component: AnsiTextView) =
            TerminalSize(maxOf(contentWidth, 1), maxOf(lines.size, 1))

        // Read-only view: no text cursor.
        override fun getCursorLocation(component: AnsiTextView): TerminalPosition? = null

        override fun drawComponent(graphics: TextGUIGraphics, component: AnsiTextView) {
            graphics.fill(' ')
            for (row in 0 until graphics.size.rows) {
                val line = lines.getOrNull(scroll + row) ?: break
                var column = 0
                for (segment in line) {
                    graphics.foregroundColor = segment.color ?: TextColor.ANSI.DEFAULT
                    graphics.backgroundColor = TextColor.ANSI.DEFAULT
                    graphics.putString(column, row, segment.text)
                    column += segment.text.length
                }
            }
        }
    }
}
