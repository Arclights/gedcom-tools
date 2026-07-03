package com.arclights.commands

import com.arclights.BirthEvent
import com.arclights.Calendars
import com.arclights.ChildToFamilyLink
import com.arclights.ChristeningEvent
import com.arclights.Date
import com.arclights.DatePhrase
import com.arclights.DateValue
import com.arclights.DeathEvent
import com.arclights.Gedcom
import com.arclights.GeneralIndividualEvent
import com.arclights.Individual
import com.arclights.IndividualEvent
import com.arclights.IndividualEventDetails
import com.arclights.SpouseToFamilyLink
import com.arclights.tui.Tui

class PersonInfo : Command {

    override fun getName() = "List person info"

    override fun run(gedcom: Gedcom, tui: Tui) {
        val person = tui.selectPerson(gedcom, "the person") ?: return
        tui.showText("Person info", personInfoText(gedcom, person))
    }
}

/** Renders everything known about [person] as a printable, multi-line block. */
fun personInfoText(gedcom: Gedcom, person: Individual): String = buildString {
    appendLine("Names:")
    person.names.forEach { appendLine("  ${it.name}") }
    appendLine("Sex: ${person.sex ?: "Unknown"}")

    person.events.forEach { appendLine(it.toDisplayString()) }

    if (person.notes.isNotEmpty()) {
        appendLine("Notes:")
        person.notes.forEach { appendLine("  $it") }
    }

    person.childToFamilies.forEach { appendLine(gedcom.describeAsChild(it)) }
    person.spouseToFamilies.forEach { appendLine(gedcom.describeAsSpouse(person, it)) }
}.trimEnd('\n')

private fun Gedcom.describeAsChild(link: ChildToFamilyLink): String {
    val family = familyGroups[link.familyId]
    val parentNames = listOfNotNull(family?.husbandId, family?.wifeId)
        .mapNotNull { individuals[it]?.names?.firstOrNull()?.name }

    return if (parentNames.isEmpty()) {
        "Child in family: ${link.familyId.value}"
    } else {
        "Child of ${parentNames.joinToString(" and ")}"
    }
}

private fun Gedcom.describeAsSpouse(person: Individual, link: SpouseToFamilyLink): String {
    val family = familyGroups[link.familyId]
    val spouseName = listOfNotNull(family?.husbandId, family?.wifeId)
        .firstOrNull { it != person.id }
        ?.let { individuals[it]?.names?.firstOrNull()?.name }

    return if (spouseName == null) {
        "Spouse in family: ${link.familyId.value}"
    } else {
        "Spouse of $spouseName"
    }
}

private fun IndividualEvent.toDisplayString(): String = when (this) {
    is BirthEvent -> "Birth: ${details.toDisplayString()}"
    is ChristeningEvent -> "Christening: ${details.toDisplayString()}"
    is DeathEvent -> "Death: ${details.toDisplayString()}"
    is GeneralIndividualEvent -> "$type: ${details.toDisplayString()}"
    else -> toString()
}

private fun IndividualEventDetails?.toDisplayString(): String {
    val dateText = this?.details?.date?.toDisplayString() ?: "unknown date"
    val placeText = this?.details?.place?.name ?: "unknown place"
    return "$dateText in $placeText"
}

private fun DateValue.toDisplayString(): String = when (this) {
    is Date -> {
        val calendarPrefix = dateCalendarEscape?.takeIf { it != Calendars.GREGORIAN }?.let { "$it " } ?: ""
        "$calendarPrefix$dateCalendar"
    }

    is DatePhrase -> text
    else -> toString()
}
