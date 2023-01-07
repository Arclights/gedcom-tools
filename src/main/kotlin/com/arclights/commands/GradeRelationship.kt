package com.arclights.commands

import com.arclights.ChildToFamilyLink
import com.arclights.FamilyGroup
import com.arclights.Gedcom
import com.arclights.Individual
import com.arclights.MultiLineEntity
import com.arclights.PrintMatrix
import com.arclights.PrintMatrixEntity
import com.arclights.SingleLineEntity.Companion.horizontalConnection
import com.arclights.SingleLineEntity.Companion.verticalConnection
import com.arclights.SpouseToFamilyLink
import com.arclights.bfs
import com.arclights.commands.GradeRelationship.RoleInRelationship.CHILD
import com.arclights.commands.GradeRelationship.RoleInRelationship.PARENT
import com.arclights.commands.GradeRelationship.RoleInRelationship.PARTNER
import com.arclights.commands.GradeRelationship.RoleInRelationship.UNKNOWN

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

        println(path.toPrintableString())
        println()
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
        return bfs(
            startEntity = RelationshipPart(UNKNOWN, from),
            identifier = { it.person.id },
            childEntities = { getAllFamilyRelatives(it.person) },
            isFinished = { it.person == to }
        )
    }

    private fun Gedcom.getAllFamilyRelatives(person: Individual): List<RelationshipPart> {
        val parents = person.childToFamilies
            .map(ChildToFamilyLink::familyId)
            .map(familyGroups::getValue)
            .flatMap { listOf(it.husbandId, it.wifeId) }
            .filterNotNull()
            .map(individuals::getValue)
            .map { RelationshipPart(PARENT, it) }

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

    enum class RoleInRelationship {
        PARENT,
        CHILD,
        PARTNER,
        UNKNOWN
    }
}

class PrintMatrixBuilder(private val printMatrix: PrintMatrix) {
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

fun PrintMatrix.builder() = PrintMatrixBuilder(this)

fun List<GradeRelationship.RelationshipPart>.toPrintableMatrix(): PrintMatrix {
    val printMatrix = PrintMatrix()
    val builder = printMatrix.builder()

    builder.setCurrent(MultiLineEntity(listOf(first().person.names.first().name)))
    drop(1).forEachIndexed { iMinus1, relationship ->
        val i = iMinus1 + 1
        val entity = MultiLineEntity(listOf(relationship.person.names.first().name))
        when (relationship.roleInRelationship) {
            PARENT -> {
                val nextRelationshipRole = getOrNull(i + 1)?.roleInRelationship
                entity.spanTwoColumns = nextRelationshipRole == CHILD
                builder.addParent(entity)
            }

            CHILD -> builder.addChild(entity)
            PARTNER -> builder.addPartner(entity)
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