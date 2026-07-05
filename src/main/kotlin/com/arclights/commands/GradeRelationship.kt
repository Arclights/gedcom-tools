package com.arclights.commands

import com.arclights.BirthEvent
import com.arclights.ChildToFamilyLink
import com.arclights.Color
import com.arclights.ColoredString
import com.arclights.DeathEvent
import com.arclights.FamilyGroup
import com.arclights.Gedcom
import com.arclights.Individual
import com.arclights.IndividualEvent
import com.arclights.IndividualEventDetails
import com.arclights.MultiLineEntity
import com.arclights.PrintMatrix
import com.arclights.PrintMatrixEntity
import com.arclights.QUAY
import com.arclights.SingleLineEntity.Companion.horizontalConnection
import com.arclights.SingleLineEntity.Companion.verticalConnection
import com.arclights.SourceCitation
import com.arclights.SpouseToFamilyLink
import com.arclights.bfs
import com.arclights.tui.Tui
import com.arclights.commands.GradeRelationship.RoleInRelationship.CHILD
import com.arclights.commands.GradeRelationship.RoleInRelationship.PARENT
import com.arclights.commands.GradeRelationship.RoleInRelationship.PARTNER
import com.arclights.commands.GradeRelationship.RoleInRelationship.UNKNOWN

class GradeRelationship : Command {

    override fun getName() = "Grade relationship"

    override fun run(gedcom: Gedcom, tui: Tui) {
        val person1 = tui.selectPerson(gedcom, "the first person") ?: return
        val person2 = tui.selectPerson(gedcom, "the second person") ?: return

        val path = gedcom.findPath(person1, person2)
        if (path == null) {
            tui.messageBox(
                "No relationship",
                "Could not find relationship between " +
                    "${person1.names.first().name} and ${person2.names.first().name}"
            )
            return
        }

        tui.showMatrix("Relationship", path.toPrintableMatrix())
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

    builder.setCurrent(first().person.toPrintMatrixEntity())
    drop(1).forEachIndexed { iMinus1, relationship ->
        val i = iMinus1 + 1
        val entity = relationship.person.toPrintMatrixEntity()
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

fun Individual.toPrintMatrixEntity(): PrintMatrixEntity {
    val (birthGrade, deathGrade, _, _, averageGrade) = getSourceGrade()
    val lines = buildList {
        add(ColoredString(names.first().name))
        birthGrade?.let {
            val date = eventDateText<BirthEvent> { event -> event.details }
            add(ColoredString("Birth: $date ($it)", it.toColor()))
        }
        deathGrade?.let {
            val date = eventDateText<DeathEvent> { event -> event.details }
            add(ColoredString("Death: $date ($it)", it.toColor()))
        }
    }
    return MultiLineEntity(lines, color = averageGrade.toColor())
}

private inline fun <reified T : IndividualEvent> Individual.eventDateText(
    details: (T) -> IndividualEventDetails?
): String =
    events.filterIsInstance<T>()
        .firstNotNullOfOrNull { details(it)?.details?.date }
        ?.toDisplayString()
        ?: "unknown date"

private fun QUAY?.toColor() = when (this) {
    QUAY.UNRELIABLE, null -> Color.RED
    QUAY.QUESTIONABLE -> Color.YELLOW
    QUAY.SECONDARY -> Color.YELLOW
    QUAY.PRIMARY -> Color.GREEN
    QUAY.DIRECT -> Color.GREEN
}

data class IndividualGrade(
    val birthGrade: QUAY?,
    val deathGrade: QUAY?,
    val minGrade: QUAY?,
    val maxGrade: QUAY?,
    val averageGrade: QUAY?
)

fun Individual.getSourceGrade(): IndividualGrade {
    val birthGrade = eventSourceGrade<BirthEvent> { it.details }
    val deathGrade = eventSourceGrade<DeathEvent> { it.details }

    val presentGrades = listOfNotNull(birthGrade, deathGrade)
    val minGrade = presentGrades.minByOrNull(QUAY::value)
    val maxGrade = presentGrades.maxByOrNull(QUAY::value)
    val averageGrade = presentGrades
        .takeIf(List<QUAY>::isNotEmpty)
        ?.let { grades -> grades.sumOf(QUAY::value) / grades.size }
        ?.let(QUAY::fromValue)

    return IndividualGrade(birthGrade, deathGrade, minGrade, maxGrade, averageGrade)
}

/**
 * The source-quality grade of the given event type, or `null` when the person has no such event.
 * A present event with no graded source citations counts as [QUAY.UNRELIABLE].
 */
private inline fun <reified T : IndividualEvent> Individual.eventSourceGrade(
    details: (T) -> IndividualEventDetails?
): QUAY? {
    val matching = events.filterIsInstance<T>()
    if (matching.isEmpty()) return null
    return matching
        .flatMap { details(it)?.details?.sourceCitations.orEmpty() }
        .mapNotNull(SourceCitation::qualityAssessment)
        .maxByOrNull(QUAY::value)
        ?: QUAY.UNRELIABLE
}