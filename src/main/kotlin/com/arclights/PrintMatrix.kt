package com.arclights

data class PrintMatrix(private val entries: MutableMap<Pair<Int, Int>, PrintMatrixEntity> = mutableMapOf()) {
    fun put(column: Int, row: Int, value: PrintMatrixEntity) {
        entries[column to row] = value
    }

    operator fun get(column: Int, row: Int) = entries[column to row]

    /** Column widths (indexed 0..maxColumn), row heights, and the occupied bounds — the shared geometry. */
    private data class Layout(
        val columnWidths: List<Int>,
        val rowHeights: Map<Int, Int>,
        val minRow: Int,
        val maxRow: Int,
        val maxColumn: Int
    )

    private fun layout(): Layout {
        val maxColumn = entries.keys.maxOf { it.first }
        val minRow = entries.keys.minOf { it.second }
        val maxRow = entries.keys.maxOf { it.second }

        val columnWidths = (0..maxColumn).map { column ->
            entries.filterKeys { it.first == column }.values.maxOfOrNull { it.width() } ?: 0
        }
        val rowHeights = (minRow..maxRow).associateWith { row ->
            entries.filterKeys { it.second == row }.values.maxOfOrNull { it.height() } ?: 1
        }
        return Layout(columnWidths, rowHeights, minRow, maxRow, maxColumn)
    }

    /** The total size, in characters, the matrix occupies when painted. */
    fun size(): MatrixSize {
        if (entries.isEmpty()) return MatrixSize(0, 0)
        val layout = layout()
        return MatrixSize(layout.columnWidths.sum(), layout.rowHeights.values.sum())
    }

    /**
     * Paints every entity onto [surface] using absolute character coordinates. Rows run top (highest
     * row index) to bottom; a cell that spans two columns is drawn at its own column but sized to
     * cover both.
     */
    fun paint(surface: CellSurface) {
        if (entries.isEmpty()) return
        val layout = layout()

        val columnX = IntArray(layout.maxColumn + 2)
        for (column in 1..layout.maxColumn + 1) {
            columnX[column] = columnX[column - 1] + layout.columnWidths[column - 1]
        }

        var y = 0
        for (row in layout.maxRow downTo layout.minRow) {
            val rowHeight = layout.rowHeights.getValue(row)
            for (column in 0..layout.maxColumn) {
                val entity = entries[column to row] ?: continue
                val cellWidth = if (entity.spanTwoColumns) {
                    layout.columnWidths[column] + layout.columnWidths[column + 1]
                } else {
                    layout.columnWidths[column]
                }
                entity.paint(surface, columnX[column], y, cellWidth, rowHeight)
            }
            y += rowHeight
        }
    }
}

/** The character dimensions a [PrintMatrix] occupies when painted. */
data class MatrixSize(val width: Int, val height: Int)

enum class Orientation { VERTICAL, HORIZONTAL }

/**
 * A renderer-agnostic surface an entity can paint itself onto, using absolute character coordinates.
 * The surface decides which glyphs/colors to actually emit (e.g. the TUI draws real box-drawing
 * characters and colors).
 */
interface CellSurface {
    fun drawBox(x: Int, y: Int, width: Int, height: Int, color: Color?)
    fun putText(x: Int, y: Int, text: String, color: Color?)
    fun putConnector(x: Int, y: Int, orientation: Orientation)
}

abstract class PrintMatrixEntity(var spanTwoColumns: Boolean = false) {
    abstract fun width(): Int

    abstract fun height(): Int

    /** Paints this entity into the [cellWidth] x [cellHeight] cell whose top-left corner is ([x], [y]). */
    abstract fun paint(surface: CellSurface, x: Int, y: Int, cellWidth: Int, cellHeight: Int)
}

data class SingleLineEntity(private val entity: String) :
    PrintMatrixEntity() {
    companion object {
        val verticalConnection = SingleLineEntity("|")
        val horizontalConnection = SingleLineEntity("-")
    }

    override fun width(): Int = entity.length
    override fun height() = 1

    override fun paint(surface: CellSurface, x: Int, y: Int, cellWidth: Int, cellHeight: Int) {
        if (entity.isEmpty()) return
        // Centered within the cell.
        val left = x + (cellWidth - entity.length) / 2
        val top = y + (cellHeight - 1) / 2
        when (entity) {
            "|" -> surface.putConnector(left, top, Orientation.VERTICAL)
            "-" -> surface.putConnector(left, top, Orientation.HORIZONTAL)
            else -> surface.putText(left, top, entity, null)
        }
    }
}

data class MultiLineEntity(
    private val lines: List<ColoredString>,
    private val margin: Int = 1,
    private val color: Color? = null
) : PrintMatrixEntity() {
    private val marginSum = margin * 2
    override fun width() = lines.maxOf(ColoredString::length) + 2 + marginSum
    override fun height() = lines.size + 2

    override fun paint(surface: CellSurface, x: Int, y: Int, cellWidth: Int, cellHeight: Int) {
        val boxX = x + margin
        val boxWidth = cellWidth - marginSum
        val boxHeight = lines.size + 2
        surface.drawBox(boxX, y, boxWidth, boxHeight, color)
        val innerWidth = boxWidth - 2
        lines.forEachIndexed { i, line ->
            val left = boxX + 1 + (innerWidth - line.length) / 2
            surface.putText(left, y + 1 + i, line.text, line.textColor)
        }
    }
}

data class ColoredString(private val string: String, private val color: Color? = null) {
    val length = string.length

    /** The raw text, without any color escapes. */
    val text get() = string

    /** The color this string should be rendered in, if any. */
    val textColor get() = color

    override fun toString() = Color.colorize(string, color)
}

enum class Color(val code: Int) {
    RED(124),
    YELLOW(220),
    GREEN(34);

    fun escapeCode() = "$escape[38:5:${code}m"

    companion object {
        private const val escape = "\u001B"
        private const val reset = "$escape[0m"
        fun colorize(s: String, color: Color?) = "${color?.escapeCode() ?: Color.reset}$s${Color.reset}"
    }
}