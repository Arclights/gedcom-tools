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

class PersonInfo : Command {

    override fun getName() = "List person info"

    override fun run(gedcom: Gedcom) {
        val person = gedcom.findPerson("the person") ?: kotlin.run {
            return
        }

        println()
        println("Names:")
        person.names.forEach { println("  ${it.name}") }
        println("Sex: ${person.sex ?: "Unknown"}")

        person.events.forEach { println(it.toDisplayString()) }

        if (person.notes.isNotEmpty()) {
            println("Notes:")
            person.notes.forEach { println("  $it") }
        }

        person.childToFamilies.forEach { println(gedcom.describeAsChild(it)) }
        person.spouseToFamilies.forEach { println(gedcom.describeAsSpouse(person, it)) }
        println()
    }

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
}
