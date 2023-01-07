package com.arclights

fun String.padTo(widthToPadTo: Int): String {
    val diff = widthToPadTo - length
    val padding = diff / 2
    val leftPadding = padding
    val rightPadding = if (diff % 2 != 0) padding + 1 else padding

    return " ".repeat(leftPadding) + this + " ".repeat(rightPadding)
}

fun <T> List<T>.padTo(toPadTo: Int, padder: T): List<T> {
    val diff = toPadTo - size
    val padding = diff / 2
    val prePadding = padding
    val postPadding = if (diff % 2 != 0) padding + 1 else padding

    return List(prePadding) { padder } + this + List(postPadding) { padder }
}

data class PrintMatrix(private val entries: MutableMap<Pair<Int, Int>, PrintMatrixEntity> = mutableMapOf()) {
    fun put(column: Int, row: Int, value: PrintMatrixEntity) {
        entries[column to row] = value
    }

    operator fun get(column: Int, row: Int) = entries[column to row]

    override fun toString(): String {
        val minColumn = 0
        val maxColumn = entries.keys.maxOf { it.first }
        val minRow = entries.keys.minOf { it.second }
        val maxRow = entries.keys.maxOf { it.second }

        val columnWidths = (minColumn..maxColumn).map { column ->
            val entitiesInColumn = entries
                .filter { (key, _) -> key.first == column }
                .values
            entitiesInColumn.maxOf { it.width() }
        }

        val rowHeights = (minRow..maxRow).associateWith { row ->
            val entitiesInRow = entries
                .filter { (key, _) -> key.second == row }
                .values
            entitiesInRow.maxOf { it.height() }
        }

        val sb = StringBuilder()

        for (row in (maxRow downTo minRow)) {
            val rowHeight = rowHeights[row]!!
            val rowLines = List(rowHeight) { StringBuilder() }

            var column = minColumn
            while (column <= maxColumn) {
                val entity = entries[column to row] ?: SingleLineEntity("")

                val columnWidth = if (entity.spanTwoColumns) {
                    columnWidths[column] + columnWidths[column + 1]
                } else {
                    columnWidths[column]
                }

                entity.toLines(columnWidth, rowHeight).forEachIndexed { i, line -> rowLines[i].append(line) }
                if (entity.spanTwoColumns) {
                    column++
                }
                column++
            }
            rowLines.forEachIndexed { i, line ->
                sb.append(line)
                if (i < rowLines.size - 1) {
                    sb.append("\n")
                }
            }
            if (row != minRow) {
                sb.append("\n")
            }
        }

        return sb.toString()
    }
}

abstract class PrintMatrixEntity(var spanTwoColumns: Boolean = false) {
    abstract fun width(): Int

    abstract fun height(): Int
    abstract fun toLines(padToWidth: Int, padToHeight: Int): List<String>
}

data class SingleLineEntity(private val entity: String) :
    PrintMatrixEntity() {
    companion object {
        val verticalConnection = SingleLineEntity("|")
        val horizontalConnection = SingleLineEntity("-")
    }

    override fun width(): Int = entity.length
    override fun height() = 1
    override fun toLines(padToWidth: Int, padToHeight: Int) =
        listOf(entity.padTo(padToWidth)).padTo(padToHeight, " ".repeat(padToWidth))
}

data class MultiLineEntity(private val lines: List<String>, private val margin: Int = 1) : PrintMatrixEntity() {
    private val marginSum = margin * 2
    override fun width() = lines.maxOf(String::length) + 2 + marginSum
    override fun height() = lines.size + 2

    override fun toLines(padToWidth: Int, padToHeight: Int): List<String> {
        val marginString = " ".repeat(margin)
        val topAndBottomLine = "$marginString+${"-".repeat(padToWidth - 2 - marginSum)}+$marginString"
        return listOf(topAndBottomLine) +
                lines.map { "$marginString|${it.padTo(padToWidth - 2 - marginSum)}|$marginString" } +
                listOf(topAndBottomLine)
    }
}