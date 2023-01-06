package com.arclights.commands

import com.arclights.ChildToFamilyLink
import com.arclights.FamilyGroup
import com.arclights.FamilyGroupId
import com.arclights.Gedcom
import com.arclights.Individual
import com.arclights.SpouseToFamilyLink
import com.arclights.commands.GradeRelationship.RoleInRelationship.CHILD
import com.arclights.commands.GradeRelationship.RoleInRelationship.PARENT
import com.arclights.commands.GradeRelationship.RoleInRelationship.PARTNER
import com.arclights.commands.GradeRelationship.RoleInRelationship.UNKNOWN
import com.arclights.commands.SingleLineEntity.Companion.horizontalConnection
import com.arclights.commands.SingleLineEntity.Companion.verticalConnection

class GradeRelationship : Command {

    override fun getName() = "Grade relationship"

    override fun run(gedcom: Gedcom) {
        val person1 = gedcom.findPerson("the first person") ?: kotlin.run {
            return
        }
        val person2 = gedcom.findPerson("the second person") ?: kotlin.run {
            return
        }
        println("Grading relationship")
        val path = gedcom.findPath(person1, person2) ?: kotlin.run {
            println("Could not find relationship between ${person1.names.first().name} and ${person2.names.first().name}")
            return
        }

        println(path)
        println(path.toPrintableString())
    }

    private fun Gedcom.findPerson(personLabel: String): Individual? {
        print("Please provide the name of $personLabel: ")
        val name = readln()
        val matchingName =
            individuals.values.filter { it.names.any { individualName -> name in individualName.name.lowercase() } }

        if (matchingName.isEmpty()) {
            println("Found no person with name '$name'")
            return null
        }

        return if (matchingName.size == 1) {
            matchingName[0]
        } else {

            println("Found multiple matches for name '$name':")
            matchingName.forEachIndexed { i, individual ->
                println("$i: ${individual.names[0].name}")
            }
            println("Enter the number of the one you want to choose:")
            val chosenIndex = readln().toInt()

            matchingName[chosenIndex]
        }
            .also { println("Found ${it.names[0].name}") }

    }

    private fun Gedcom.findPath(from: Individual, to: Individual): List<RelationshipPart>? {
        val exploredIds = mutableSetOf(from.id)
        val queue = queueOf(Node(null, RelationshipPart(UNKNOWN, from)))
        while (queue.isEmpty().not()) {
            val v = queue.dequeue()
            println("at person ${v.value.person.names.first().name}")
            if (v.value.person == to) {
                println("is target, returning")
                return v.getPath()
            }
            getAllFamilyRelatives(v.value.person)
                .also { println("direct relatives: $it") }
                .filterNot { it.person.id in exploredIds }
                .forEach { w ->
                    println("queueing to explore: ${w.person.names.first().name}")
                    exploredIds.add(w.person.id)
                    queue.enqueue(Node(v, w))
                }
        }
        return null
    }

    private fun Gedcom.getAllFamilyRelatives(person: Individual): List<RelationshipPart> {
        println("is child in families: ${person.childToFamilies.map { it.familyId }.map(FamilyGroupId::value)}")
        val parents = person.childToFamilies
            .map(ChildToFamilyLink::familyId)
            .map(familyGroups::getValue)
            .flatMap { listOf(it.husbandId, it.wifeId) }
            .filterNotNull()
            .map(individuals::getValue)
            .map { RelationshipPart(PARENT, it) }

        println(
            "is spouse in families: ${
                person.spouseToFamilies.map(SpouseToFamilyLink::familyId).map(FamilyGroupId::value)
            }"
        )
        val children = person.spouseToFamilies
            .map(SpouseToFamilyLink::familyId)
            .map(familyGroups::getValue)
            .flatMap(FamilyGroup::childrenIds)
            .map(individuals::getValue)
            .map { RelationshipPart(CHILD, it) }

        val partner = person.spouseToFamilies
            .map(SpouseToFamilyLink::familyId)
            .map(familyGroups::getValue)
            .flatMap { listOf(it.husbandId, it.wifeId) }
            .filterNotNull()
            .filterNot(person.id::equals)
            .map(individuals::getValue)
            .map { RelationshipPart(PARTNER, it) }

        return parents + children + partner
    }

    data class RelationshipPart(val roleInRelationship: RoleInRelationship, val person: Individual)

    data class Node<T>(val parent: Node<T>?, val value: T) {
        fun getPath(): List<T> = (parent?.getPath() ?: listOf()) + value
    }

    private fun <T> queueOf(vararg items: T) = mutableListOf(*items)
    private fun <T> MutableList<T>.dequeue(): T = removeFirst()
    private fun <T> MutableList<T>.enqueue(item: T) = add(item)

    enum class RoleInRelationship {
        PARENT,
        CHILD,
        PARTNER,
        UNKNOWN
    }
}

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

class PrintMatrixIterator(private val printMatrix: PrintMatrix) {
    private var column = 0
    private var row = 0

    fun setCurrent(item: PrintMatrixEntity) {
        printMatrix.put(column, row, item)
    }

    fun addChild(item: PrintMatrixEntity) {
        row--
        printMatrix.put(column, row, verticalConnection)
        row--
        printMatrix.put(column, row, verticalConnection)
        row--
        printMatrix.put(column, row, item)
    }

    fun addParent(item: PrintMatrixEntity) {
        row++
        printMatrix.put(column, row, verticalConnection)
        row++
        printMatrix.put(column, row, verticalConnection)
        row++
        printMatrix.put(column, row, item)
        if (item.spanTwoColumns) {
            column++
        }
    }

    fun addPartner(item: PrintMatrixEntity) {
        column++
        printMatrix.put(column, row, horizontalConnection)
        column++
        printMatrix.put(column, row, item)
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

fun PrintMatrix.iterator() = PrintMatrixIterator(this)

fun List<GradeRelationship.RelationshipPart>.toPrintableMatrix(): PrintMatrix {
    val printMatrix = PrintMatrix()
    val iterator = printMatrix.iterator()

    iterator.setCurrent(MultiLineEntity(listOf(first().person.names.first().name)))
    drop(1).forEachIndexed { iMinus1, relationship ->
        val i = iMinus1 + 1
        val entity = MultiLineEntity(listOf(relationship.person.names.first().name))
        when (relationship.roleInRelationship) {
            PARENT -> {
                val nextRelationshipRole = getOrNull(i + 1)?.roleInRelationship
                entity.spanTwoColumns = nextRelationshipRole == CHILD
                iterator.addParent(entity)
            }

            CHILD -> iterator.addChild(entity)
            PARTNER -> iterator.addPartner(entity)
            UNKNOWN -> throw IllegalStateException(
                "Person ${relationship.person.names.first().name} has relationship role UNKNOWN"
            )
        }
    }

    return printMatrix
}

fun List<GradeRelationship.RelationshipPart>.toPrintableString(): String {
    val matrix = toPrintableMatrix()
    return matrix.toString()
}