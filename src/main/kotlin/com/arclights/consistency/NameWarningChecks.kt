package com.arclights.consistency

import com.arclights.Gedcom

/**
 * A married woman whose recorded surname (the maiden name) matches her husband's surname, suggesting
 * the married name was entered where the maiden name belongs.
 */
object MarriedNameAsMaidenCheck : ConsistencyCheck {
    override val name = "Married name entered as maiden name"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.familyGroups.values.mapNotNull { family ->
            val husband = gedcom.father(family) ?: return@mapNotNull null
            val wife = gedcom.mother(family) ?: return@mapNotNull null
            val husbandSurname = husband.names.firstNotNullOfOrNull { it.surnameValue() } ?: return@mapNotNull null
            val wifeSurname = wife.names.firstNotNullOfOrNull { it.surnameValue() } ?: return@mapNotNull null
            if (husbandSurname.equals(wifeSurname, ignoreCase = true)) {
                "${wife.displayName()} shares the surname '$wifeSurname' with her spouse " +
                    "${husband.displayName()}; a married name may have been entered as the maiden name"
            } else null
        }
}

/** A title (Dr, Rev, Sir, ...) that has been entered as part of the given name. */
object TitleInGivenNameCheck : ConsistencyCheck {
    override val name = "Title in given name"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.individuals.values.mapNotNull { person ->
            val title = person.names
                .mapNotNull { it.givenName() }
                .firstNotNullOfOrNull { titlesIn(it).firstOrNull() }
                ?: return@mapNotNull null
            "${person.displayName()} has a title ('$title') in their given name"
        }
}

/** A title (Dr, Rev, Sir, ...) that has been entered as part of the surname. */
object TitleInSurnameCheck : ConsistencyCheck {
    override val name = "Title in surname"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.individuals.values.mapNotNull { person ->
            val title = person.names
                .mapNotNull { it.surnameValue() }
                .firstNotNullOfOrNull { titlesIn(it).firstOrNull() }
                ?: return@mapNotNull null
            "${person.displayName()} has a title ('$title') in their surname"
        }
}

/** More than one birth event recorded for a person. */
object MultipleBirthEventsCheck : ConsistencyCheck {
    override val name = "Several birth events"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.individuals.values
            .filter { it.birthEvents().size > 1 }
            .map { "${it.displayName()} has ${it.birthEvents().size} birth events recorded" }
}

/** More than one death event recorded for a person. */
object MultipleDeathEventsCheck : ConsistencyCheck {
    override val name = "Several death events"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.individuals.values
            .filter { it.deathEvents().size > 1 }
            .map { "${it.displayName()} has ${it.deathEvents().size} death events recorded" }
}
