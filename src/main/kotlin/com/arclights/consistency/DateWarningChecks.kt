package com.arclights.consistency

import com.arclights.BirthEvent
import com.arclights.DeathEvent
import com.arclights.FamilyGroup
import com.arclights.Gedcom
import com.arclights.Individual

/** Died at an implausibly old age. */
object DiedTooOldCheck : ConsistencyCheck {
    override val name = "Died too old"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.individuals.values.mapNotNull { person ->
            val birth = person.birthDate() ?: return@mapNotNull null
            val death = person.deathDate() ?: return@mapNotNull null
            val age = yearsBetween(birth, death)
            if (age > config.maxDeathAge) {
                "${person.displayName()} died at age $age, older than the configured maximum of ${config.maxDeathAge}"
            } else null
        }
}

/** No recorded death and would be implausibly old today. */
object AliveTooOldCheck : ConsistencyCheck {
    override val name = "Alive but too old"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.individuals.values
            .filter { it.deathEvents().isEmpty() }
            .mapNotNull { person ->
                val birth = person.birthDate() ?: return@mapNotNull null
                val age = yearsBetween(birth, config.today)
                if (age > config.maxLivingAge) {
                    "${person.displayName()} would be $age years old today with no recorded death, " +
                        "older than the configured maximum of ${config.maxLivingAge}"
                } else null
            }
}

/** A parent younger than the configured minimum when a child was born. */
object ParentTooYoungCheck : ConsistencyCheck {
    override val name = "Parent too young at child's birth"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.eachParentChildAge { parent, child, age ->
            // Negative ages (child born before parent) are the domain of ChildOlderThanParentCheck.
            if (age in 0 until config.minParentAge) {
                "${parent.displayName()} was only $age years old when their child " +
                    "${child.displayName()} was born (minimum ${config.minParentAge})"
            } else null
        }
}

/** A parent older than the configured maximum when a child was born. */
object ParentTooOldCheck : ConsistencyCheck {
    override val name = "Parent too old at child's birth"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.eachParentChildAge { parent, child, age ->
            if (age > config.maxParentAge) {
                "${parent.displayName()} was $age years old when their child " +
                    "${child.displayName()} was born (maximum ${config.maxParentAge})"
            } else null
        }
}

/** An event dated before the person was born. Birth and death events are excluded (see the ordering checks). */
object EventBeforeBirthCheck : ConsistencyCheck {
    override val name = "Event before birth"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.individuals.values.flatMap { person ->
            val birth = person.birthDate() ?: return@flatMap emptyList()
            person.events
                .filterNot { it is BirthEvent || it is DeathEvent }
                .mapNotNull { event ->
                    val date = event.date() ?: return@mapNotNull null
                    if (date.isBefore(birth)) {
                        "${person.displayName()} has a ${event.label()} event ($date) before their birth ($birth)"
                    } else null
                }
        }
}

/** An event dated after the person had died. Birth and death events are excluded (see the ordering checks). */
object EventAfterDeathCheck : ConsistencyCheck {
    override val name = "Event after death"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.individuals.values.flatMap { person ->
            val death = person.deathDate() ?: return@flatMap emptyList()
            person.events
                .filterNot { it is BirthEvent || it is DeathEvent }
                .mapNotNull { event ->
                    val date = event.date() ?: return@mapNotNull null
                    if (date.isAfter(death)) {
                        "${person.displayName()} has a ${event.label()} event ($date) after their death ($death)"
                    } else null
                }
        }
}

/** A date anywhere in the tree that lies in the future. */
object FutureDateCheck : ConsistencyCheck {
    override val name = "Date in the future"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> {
        val individualIssues = gedcom.individuals.values.flatMap { person ->
            person.events.mapNotNull { event ->
                val date = event.date() ?: return@mapNotNull null
                if (date.isAfter(config.today)) {
                    "${person.displayName()} has a ${event.label()} event dated in the future ($date)"
                } else null
            }
        }

        val familyIssues = gedcom.familyGroups.values.flatMap { family ->
            family.events.mapNotNull { event ->
                val date = event.date() ?: return@mapNotNull null
                if (date.isAfter(config.today)) {
                    "${gedcom.describeCouple(family)} has a ${event.eventType.name} event dated in the future ($date)"
                } else null
            }
        }

        return individualIssues + familyIssues
    }
}

/**
 * Visits every (parent, child) pair that has both birth dates known and hands the parent's age at the
 * child's birth to [selector]. Keeps the age-based family checks free of the shared traversal.
 */
private fun Gedcom.eachParentChildAge(
    selector: (parent: Individual, child: Individual, age: Long) -> String?
): List<String> =
    familyGroups.values.flatMap { family ->
        val children = children(family)
        parents(family).flatMap { parent ->
            val parentBirth = parent.birthDate() ?: return@flatMap emptyList()
            children.mapNotNull { child ->
                val childBirth = child.birthDate() ?: return@mapNotNull null
                selector(parent, child, yearsBetween(parentBirth, childBirth))
            }
        }
    }

/** A readable label for a family group, e.g. "The family of John /Doe/ and Jane /Roe/". */
internal fun Gedcom.describeCouple(family: FamilyGroup): String {
    val names = parents(family).map(Individual::displayName)
    return when {
        names.isEmpty() -> "The family ${family.id.value}"
        else -> "The family of ${names.joinToString(" and ")}"
    }
}
