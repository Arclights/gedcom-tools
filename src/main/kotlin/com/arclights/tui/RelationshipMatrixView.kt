package com.arclights.tui

import com.arclights.CellSurface
import com.arclights.Color
import com.arclights.Orientation
import com.arclights.PrintMatrix
import com.googlecode.lanterna.Symbols
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractInteractableComponent
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.InteractableRenderer
import com.googlecode.lanterna.gui2.TextGUIGraphics
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType

private fun Color.toTextColor(): TextColor = TextColor.Indexed(code)

/**
 * Draws a [PrintMatrix] onto a Lanterna surface: person boxes become real box-drawing rectangles
 * colored by their source grade, and the tree connectors become line-drawing characters. Handles
 * clipping and the current scroll offset so only the visible portion is rendered.
 */
private class GraphicsCellSurface(
    private val graphics: TextGUIGraphics,
    private val offsetX: Int,
    private val offsetY: Int
) : CellSurface {

    private fun put(x: Int, y: Int, char: Char, color: TextColor?) {
        val screenX = x - offsetX
        val screenY = y - offsetY
        if (screenX < 0 || screenY < 0 || screenX >= graphics.size.columns || screenY >= graphics.size.rows) return
        graphics.foregroundColor = color ?: TextColor.ANSI.DEFAULT
        graphics.backgroundColor = TextColor.ANSI.DEFAULT
        graphics.setCharacter(screenX, screenY, char)
    }

    override fun drawBox(x: Int, y: Int, width: Int, height: Int, color: Color?) {
        if (width < 2 || height < 2) return
        val c = color?.toTextColor()
        val right = x + width - 1
        val bottom = y + height - 1
        put(x, y, Symbols.SINGLE_LINE_TOP_LEFT_CORNER, c)
        put(right, y, Symbols.SINGLE_LINE_TOP_RIGHT_CORNER, c)
        put(x, bottom, Symbols.SINGLE_LINE_BOTTOM_LEFT_CORNER, c)
        put(right, bottom, Symbols.SINGLE_LINE_BOTTOM_RIGHT_CORNER, c)
        for (i in 1 until width - 1) {
            put(x + i, y, Symbols.SINGLE_LINE_HORIZONTAL, c)
            put(x + i, bottom, Symbols.SINGLE_LINE_HORIZONTAL, c)
        }
        for (j in 1 until height - 1) {
            put(x, y + j, Symbols.SINGLE_LINE_VERTICAL, c)
            put(right, y + j, Symbols.SINGLE_LINE_VERTICAL, c)
        }
    }

    override fun putText(x: Int, y: Int, text: String, color: Color?) {
        val c = color?.toTextColor()
        text.forEachIndexed { i, char -> put(x + i, y, char, c) }
    }

    override fun putConnector(x: Int, y: Int, orientation: Orientation) {
        val char = when (orientation) {
            Orientation.VERTICAL -> Symbols.SINGLE_LINE_VERTICAL
            Orientation.HORIZONTAL -> Symbols.SINGLE_LINE_HORIZONTAL
        }
        put(x, y, char, null)
    }
}

/**
 * A focusable, scrollable component that renders a relationship [matrix] with native TUI drawing.
 * Arrow keys scroll in both axes, PageUp/PageDown scroll vertically, Home/End jump to the top/bottom,
 * and Escape invokes [onClose]; other keys fall through so Tab can reach the Close button.
 */
class RelationshipMatrixView(private val matrix: PrintMatrix, private val onClose: () -> Unit) :
    AbstractInteractableComponent<RelationshipMatrixView>() {

    private val dimensions = matrix.size()
    private var scrollX = 0
    private var scrollY = 0

    private fun viewportRows() = size?.rows ?: 1
    private fun viewportColumns() = size?.columns ?: 1
    private fun maxScrollX() = maxOf(0, dimensions.width - viewportColumns())
    private fun maxScrollY() = maxOf(0, dimensions.height - viewportRows())

    override fun handleKeyStroke(keyStroke: KeyStroke): Interactable.Result {
        when (keyStroke.keyType) {
            KeyType.ArrowDown -> scrollY = minOf(scrollY + 1, maxScrollY())
            KeyType.ArrowUp -> scrollY = maxOf(scrollY - 1, 0)
            KeyType.ArrowRight -> scrollX = minOf(scrollX + 1, maxScrollX())
            KeyType.ArrowLeft -> scrollX = maxOf(scrollX - 1, 0)
            KeyType.PageDown -> scrollY = minOf(scrollY + viewportRows(), maxScrollY())
            KeyType.PageUp -> scrollY = maxOf(scrollY - viewportRows(), 0)
            KeyType.Home -> { scrollX = 0; scrollY = 0 }
            KeyType.End -> scrollY = maxScrollY()
            KeyType.Escape -> onClose()
            else -> return super.handleKeyStroke(keyStroke)
        }
        return Interactable.Result.HANDLED
    }

    override fun createDefaultRenderer() = object : InteractableRenderer<RelationshipMatrixView> {
        override fun getPreferredSize(component: RelationshipMatrixView) =
            TerminalSize(maxOf(dimensions.width, 1), maxOf(dimensions.height, 1))

        // Read-only view: no text cursor.
        override fun getCursorLocation(component: RelationshipMatrixView) = null

        override fun drawComponent(graphics: TextGUIGraphics, component: RelationshipMatrixView) {
            graphics.fill(' ')
            matrix.paint(GraphicsCellSurface(graphics, scrollX, scrollY))
        }
    }
}
