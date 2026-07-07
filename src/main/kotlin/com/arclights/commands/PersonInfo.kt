package com.arclights.commands

import com.arclights.BirthEvent
import com.arclights.Calendars
import com.arclights.ChildToFamilyLink
import com.arclights.ChristeningEvent
import com.arclights.Color
import com.arclights.ColoredString
import com.arclights.Date
import com.arclights.DateApproximated
import com.arclights.DatePeriod
import com.arclights.DatePhrase
import com.arclights.DatePhraseExt
import com.arclights.DateRangeAfter
import com.arclights.DateRangeBefore
import com.arclights.DateRangeBetween
import com.arclights.DateValue
import com.arclights.DeathEvent
import com.arclights.EventDetail
import com.arclights.FrenchCalendar
import com.arclights.Gedcom
import com.arclights.GeneralIndividualAttribute
import com.arclights.GeneralIndividualEvent
import com.arclights.GregorianCalendar
import com.arclights.HebrewCalendar
import com.arclights.Individual
import com.arclights.IndividualEvent
import com.arclights.IndividualEventDetails
import com.arclights.IndividualEventType
import com.arclights.IndividualId
import com.arclights.IndividualName
import com.arclights.JulianCalendar
import com.arclights.AttributeType
import com.arclights.Sex
import com.arclights.SourceCitation
import com.arclights.SpouseToFamilyLink
import com.arclights.tui.Tui

class PersonInfo : Command {

    override fun getName() = "List person info"

    override fun run(gedcom: Gedcom, tui: Tui) {
        val person = tui.selectPerson(gedcom, "the person") ?: return
        tui.showPersonInfo(personCard(gedcom, person))
    }
}

// --- Model ---------------------------------------------------------------------------------------
// A view-agnostic description of a person, so the same data can be rendered as plain text or as
// native TUI components.

data class PersonCard(val name: String, val subtitle: String?, val sections: List<PersonSection>)

data class PersonSection(val title: String, val rows: List<PersonRow>)

sealed interface PersonRow
data class LabeledRow(val label: String, val value: String) : PersonRow
data class NoteRow(val text: String) : PersonRow

/** Gathers everything known about [person] into a [PersonCard]. */
fun personCard(gedcom: Gedcom, person: Individual): PersonCard = PersonCard(
    name = person.names.firstOrNull()?.headerName() ?: person.id.value,
    subtitle = person.subtitle(),
    sections = listOfNotNull(
        section("Events", person.eventRows()),
        section("Facts", person.factRows()),
        section("Family", gedcom.familyRows(person)),
        section("Associations", gedcom.associationRows(person)),
        section("Notes", person.notes.map { NoteRow(it) }),
        section("Sources", gedcom.sourceRows(person)),
        section("Also known as", person.otherNameRows()),
        section("Identifiers", person.identifierRows()),
        section("Record", person.recordRows())
    )
)

private fun section(title: String, rows: List<PersonRow>): PersonSection? =
    rows.takeIf { it.isNotEmpty() }?.let { PersonSection(title, it) }

// --- Plain-text rendering ------------------------------------------------------------------------

/** Renders a [person] as a colored, sectioned, printable card (used for logs/tests). */
fun personInfoText(gedcom: Gedcom, person: Individual): String = renderText(personCard(gedcom, person))

private fun renderText(card: PersonCard): String = buildString {
    appendLine(ColoredString(card.name, Color.CYAN).toString())
    if (card.subtitle != null) appendLine(ColoredString(card.subtitle, Color.GREY).toString())
    val ruleWidth = maxOf(card.name.length, card.subtitle?.length ?: 0)
    appendLine(ColoredString("─".repeat(ruleWidth), Color.GREY).toString())

    card.sections.forEach { section ->
        appendLine()
        appendLine(ColoredString(section.title, Color.BLUE).toString())
        val labelWidth = section.rows.filterIsInstance<LabeledRow>().maxOfOrNull { it.label.length } ?: 0
        section.rows.forEach { row ->
            when (row) {
                is LabeledRow -> appendLine("  ${row.label.padEnd(labelWidth)}   ${row.value}")
                is NoteRow -> appendLine("  • ${row.text}")
            }
        }
    }
}.trimEnd('\n')

// --- Section builders ----------------------------------------------------------------------------

/** e.g. "♂ Male  ·  1947–1998 (aged 51)". Null when neither sex nor any life dates are known. */
private fun Individual.subtitle(): String? {
    val parts = listOfNotNull(sex?.label(), lifespan())
    return parts.joinToString("  ·  ").ifBlank { null }
}

private fun Individual.lifespan(): String? {
    val birth = events.filterIsInstance<BirthEvent>().firstNotNullOfOrNull { it.details?.date() }
    val death = events.filterIsInstance<DeathEvent>().firstNotNullOfOrNull { it.details?.date() }
    val birthYear = birth?.ymd()?.year
    val deathYear = death?.ymd()?.year

    return when {
        birthYear != null && deathYear != null -> {
            val age = ageInYears(birth.ymd(), death.ymd())?.let { " (aged $it)" } ?: ""
            "$birthYear–$deathYear$age"
        }

        birthYear != null -> "b. $birthYear"
        deathYear != null -> "d. $deathYear"
        else -> null
    }
}

private fun Individual.eventRows(): List<PersonRow> = events
    .map { LabeledRowWithYear(it.eventLabel(), it.eventValue(), it.eventDetails()?.date()?.ymd()?.year) }
    // Order chronologically; events without a usable year keep their original (file) order at the end.
    .sortedWith(compareBy(nullsLast()) { it.year })
    .map { LabeledRow(it.label, it.value) }

private data class LabeledRowWithYear(val label: String, val value: String, val year: Int?)

/** "12 JAN 1998 · Lund · pneumonia · aged 51" — date, place, cause and age, dropping whichever are absent. */
private fun IndividualEvent.eventValue(): String {
    val details = eventDetails()
    val detail = details?.details
    val parts = listOfNotNull(
        detail?.date?.toDisplayString(),
        detail?.place?.name,
        detail?.cause,
        details?.age?.let { "aged $it" }
    )
    return parts.joinToString(" · ").ifBlank { "—" }
}

private fun Gedcom.associationRows(person: Individual): List<PersonRow> = person.associations.map { assoc ->
    LabeledRow(assoc.relation?.ifBlank { null } ?: "Associate", nameOf(assoc.individualId) ?: assoc.individualId.value)
}

private fun Gedcom.sourceRows(person: Individual): List<PersonRow> =
    person.sourceCitations.map { NoteRow(describeCitation(it)) }

private fun Gedcom.describeCitation(citation: SourceCitation): String {
    val title = citation.source?.let { sources[it]?.title ?: it.value } ?: citation.description ?: "Source"
    val page = citation.page?.ifBlank { null }?.let { ", p. $it" } ?: ""
    return "$title$page"
}

private fun Individual.identifierRows(): List<PersonRow> = buildList {
    add(LabeledRow("ID", id.value))
    references.forEach { add(LabeledRow("Reference", it)) }
    ancestralFileNumber?.let { add(LabeledRow("Ancestral file", it)) }
    permanentRecordFileNumber?.let { add(LabeledRow("Record file", it)) }
}

private fun Individual.recordRows(): List<PersonRow> = buildList {
    restriction?.let { add(LabeledRow("Restriction", it)) }
    changeDate?.let { add(LabeledRow("Last changed", it.toString())) }
}

private fun Individual.factRows(): List<PersonRow> = attributes
    .filterIsInstance<GeneralIndividualAttribute>()
    .map { attr ->
        val extra = attr.detail.whenAndWhereOrNull()
        val value = listOfNotNull(attr.value.ifBlank { null }, extra).joinToString(" · ").ifBlank { "—" }
        LabeledRow(humanizeTag(attr.type), value)
    }

private fun Gedcom.familyRows(person: Individual): List<PersonRow> = buildList {
    person.childToFamilies.forEach { link -> add(childRow(link)) }
    person.spouseToFamilies.forEach { link -> addAll(spouseRows(person, link)) }
}

private fun Gedcom.childRow(link: ChildToFamilyLink): PersonRow {
    val family = familyGroups[link.familyId]
    val parents = listOfNotNull(family?.husbandId, family?.wifeId).mapNotNull { nameOf(it) }
    return LabeledRow("Parents", parents.joinToString(" & ").ifBlank { "unknown (family ${link.familyId.value})" })
}

private fun Gedcom.spouseRows(person: Individual, link: SpouseToFamilyLink): List<PersonRow> {
    val family = familyGroups[link.familyId]
    val spouse = listOfNotNull(family?.husbandId, family?.wifeId)
        .firstOrNull { it != person.id }
        ?.let { nameOf(it) }
    val children = family?.childrenIds?.mapNotNull { nameOf(it) }.orEmpty()

    return buildList {
        add(LabeledRow("Spouse", spouse ?: "unknown (family ${link.familyId.value})"))
        if (children.isNotEmpty()) add(LabeledRow("Children", children.joinToString(", ")))
    }
}

private fun Individual.otherNameRows(): List<PersonRow> =
    names.drop(1).map { NoteRow(it.displayName()) }

private fun Gedcom.nameOf(id: IndividualId): String? =
    individuals[id]?.names?.firstOrNull()?.displayName()

// --- Formatting helpers --------------------------------------------------------------------------

/** Turns a GEDCOM name ("John /Doe/") into a plain display name ("John Doe"). */
private fun IndividualName.displayName(): String =
    name.replace("/", " ").replace(Regex("\\s+"), " ").trim().ifBlank { name }

/** Like [displayName] but folds in a nickname when present, e.g. `John "Johnny" Doe`. */
private fun IndividualName.headerName(): String {
    val base = displayName()
    return nickname?.ifBlank { null }?.let { "$base \"$it\"" } ?: base
}

private fun Sex.label(): String? = when (this) {
    Sex.MALE -> "♂ Male"
    Sex.FEMALE -> "♀ Female"
    Sex.INTERSEX -> "Intersex"
    Sex.UNKNOWN, Sex.NOT_RECORDED -> null
}

private fun IndividualEvent.eventLabel(): String = when (this) {
    is BirthEvent -> "Birth"
    is ChristeningEvent -> "Christening"
    is DeathEvent -> "Death"
    is GeneralIndividualEvent -> humanizeTag(type)
    else -> this::class.simpleName ?: "Event"
}

private fun IndividualEvent.eventDetails(): IndividualEventDetails? = when (this) {
    is BirthEvent -> details
    is ChristeningEvent -> details
    is DeathEvent -> details
    is GeneralIndividualEvent -> details
    else -> null
}

private fun IndividualEventDetails.date(): DateValue? = details.date

private fun EventDetail?.whenAndWhereOrNull(): String? {
    val date = this?.date?.toDisplayString()
    val place = this?.place?.name
    return listOfNotNull(date, place).joinToString(" · ").ifBlank { null }
}

/** Converts an enum tag (e.g. "OCCU", "PHYSICAL_DESCRIPTION") into a readable label. */
private fun humanizeTag(tag: String): String =
    (IndividualEventType.fromTagName(tag) ?: AttributeType.fromTagName(tag))
        ?.let { type -> (type as Enum<*>).name.titleCase() }
        ?: tag

private fun String.titleCase(): String =
    split('_').joinToString(" ") { it.lowercase() }.replaceFirstChar { it.uppercase() }

// --- Dates ---------------------------------------------------------------------------------------

private data class Ymd(val year: Int, val month: Int?, val day: Int?)

/** The single anchoring [Date] of a value (the start of a period/range, the base of an estimate). */
private fun DateValue.anchorDate(): Date? = when (this) {
    is Date -> this
    is DatePeriod -> from
    is DateRangeBefore -> date
    is DateRangeAfter -> date
    is DateRangeBetween -> date1
    is DateApproximated -> date
    is DatePhraseExt -> date
    else -> null
}

private fun DateValue.ymd(): Ymd? = anchorDate()?.let { date ->
    when (val calendar = date.dateCalendar) {
        is GregorianCalendar -> calendar.year?.newStyle?.let { Ymd(it, calendar.month?.monthNumber(), calendar.day) }
        is JulianCalendar -> calendar.year?.let { Ymd(it, calendar.month?.monthNumber(), calendar.day) }
        is HebrewCalendar -> calendar.year?.let { Ymd(it, null, null) }
        is FrenchCalendar -> calendar.year?.let { Ymd(it, null, null) }
        else -> null
    }
}

private fun GregorianCalendar.Month.monthNumber() = ordinal + 1

private fun ageInYears(birth: Ymd?, death: Ymd?): Int? {
    if (birth == null || death == null) return null
    var years = death.year - birth.year
    if (birth.month != null && death.month != null) {
        val beforeBirthday = death.month < birth.month ||
            (death.month == birth.month && birth.day != null && death.day != null && death.day < birth.day)
        if (beforeBirthday) years--
    }
    return years.takeIf { it >= 0 }
}

internal fun DateValue.toDisplayString(): String = when (this) {
    is Date -> {
        val calendarPrefix = dateCalendarEscape?.takeIf { it != Calendars.GREGORIAN }?.let { "$it " } ?: ""
        "$calendarPrefix$dateCalendar"
    }

    is DatePeriod -> "from ${from.toDisplayString()} to ${to.toDisplayString()}"
    is DateRangeBefore -> "before ${date.toDisplayString()}"
    is DateRangeAfter -> "after ${date.toDisplayString()}"
    is DateRangeBetween -> "between ${date1.toDisplayString()} and ${date2.toDisplayString()}"
    is DateApproximated -> "about ${date.toDisplayString()}"
    is DatePhraseExt -> phrase
    is DatePhrase -> text
    else -> toString()
}
