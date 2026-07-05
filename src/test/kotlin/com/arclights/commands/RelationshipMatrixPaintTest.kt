package com.arclights.commands

import com.arclights.CellSurface
import com.arclights.Color
import com.arclights.Individual
import com.arclights.IndividualId
import com.arclights.IndividualName
import com.arclights.Orientation
import com.arclights.commands.GradeRelationship.RelationshipPart
import com.arclights.commands.GradeRelationship.RoleInRelationship
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Guards the geometry of [com.arclights.PrintMatrix.paint] against a golden ASCII rendering: painting
 * onto a char grid with plain box/line glyphs must reproduce the expected relationship diagram.
 */
class RelationshipMatrixPaintTest {

    /** A [CellSurface] that paints into a char grid using plain ASCII glyphs, for a text-comparable diagram. */
    private class GridSurface(width: Int, height: Int) : CellSurface {
        private val grid = Array(height) { CharArray(width) { ' ' } }

        private fun put(x: Int, y: Int, char: Char) {
            if (y in grid.indices && x in grid[y].indices) grid[y][x] = char
        }

        override fun drawBox(x: Int, y: Int, width: Int, height: Int, color: Color?) {
            val right = x + width - 1
            val bottom = y + height - 1
            put(x, y, '+'); put(right, y, '+'); put(x, bottom, '+'); put(right, bottom, '+')
            for (i in 1 until width - 1) {
                put(x + i, y, '-'); put(x + i, bottom, '-')
            }
            for (j in 1 until height - 1) {
                put(x, y + j, '|'); put(right, y + j, '|')
            }
        }

        override fun putText(x: Int, y: Int, text: String, color: Color?) {
            text.forEachIndexed { i, char -> put(x + i, y, char) }
        }

        override fun putConnector(x: Int, y: Int, orientation: Orientation) {
            put(x, y, if (orientation == Orientation.VERTICAL) '|' else '-')
        }

        fun render() = grid.joinToString("\n") { String(it).trimEnd() }
    }

    @Test
    fun paintReproducesTheAsciiLayout() {
        // A path exercising every placement: a parent that spans two columns (because a child
        // follows), the child itself, and a partner reached over a horizontal connector.
        val path = listOf(
            part(RoleInRelationship.UNKNOWN, "person1"),
            part(RoleInRelationship.PARENT, "person2"),
            part(RoleInRelationship.CHILD, "person3"),
            part(RoleInRelationship.PARTNER, "person4")
        )
        val matrix = path.toPrintableMatrix()

        val size = matrix.size()
        val surface = GridSurface(size.width, size.height)
        matrix.paint(surface)

        assertThat(surface.render()).isEqualTo(golden("/relationship-matrix.txt"))
    }

    private fun part(role: RoleInRelationship, name: String) =
        RelationshipPart(role, Individual(id = IndividualId(name), names = listOf(IndividualName(name))))

    private fun golden(resource: String) =
        javaClass.getResource(resource)!!.readText()
            .lineSequence()
            .joinToString("\n") { it.trimEnd() }
            .trimEnd('\n')
}
