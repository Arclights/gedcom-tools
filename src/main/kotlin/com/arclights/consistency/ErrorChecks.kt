package com.arclights.consistency

import com.arclights.Gedcom

/** Death recorded before birth. */
object DeathBeforeBirthCheck : ConsistencyCheck {
    override val name = "Death before birth"
    override val severity = Severity.ERROR

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.individuals.values.mapNotNull { person ->
            val birth = person.birthDate() ?: return@mapNotNull null
            val death = person.deathDate() ?: return@mapNotNull null
            if (death.isBefore(birth)) {
                "${person.displayName()}: death ($death) is before birth ($birth)"
            } else null
        }
}

/** A child born before one of their parents. */
object ChildOlderThanParentCheck : ConsistencyCheck {
    override val name = "Child older than parent"
    override val severity = Severity.ERROR

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.familyGroups.values.flatMap { family ->
            val children = gedcom.children(family)
            gedcom.parents(family).flatMap { parent ->
                val parentBirth = parent.birthDate() ?: return@flatMap emptyList()
                children.mapNotNull { child ->
                    val childBirth = child.birthDate() ?: return@mapNotNull null
                    if (childBirth.isBefore(parentBirth)) {
                        "${child.displayName()} was born ($childBirth) before their parent " +
                            "${parent.displayName()} ($parentBirth)"
                    } else null
                }
            }
        }
}

/**
 * A child born after a parent had died. Fathers are given a gestation grace period
 * ([ConsistencyConfig.fatherPosthumousGraceDays]) since a posthumous birth is possible; mothers are not.
 */
object ChildBornAfterParentDeathCheck : ConsistencyCheck {
    override val name = "Child born after death of parent"
    override val severity = Severity.ERROR

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.familyGroups.values.flatMap { family ->
            val children = gedcom.children(family)
            val father = gedcom.father(family)
            val mother = gedcom.mother(family)

            val fatherIssues = father?.deathDate()?.let { fatherDeath ->
                val latestPlausible = fatherDeath.plusDays(config.fatherPosthumousGraceDays.toLong())
                children.mapNotNull { child ->
                    val childBirth = child.birthDate() ?: return@mapNotNull null
                    if (childBirth.isAfter(latestPlausible)) {
                        "${child.displayName()} was born ($childBirth) after the death of their father " +
                            "${father.displayName()} ($fatherDeath)"
                    } else null
                }
            }.orEmpty()

            val motherIssues = mother?.deathDate()?.let { motherDeath ->
                children.mapNotNull { child ->
                    val childBirth = child.birthDate() ?: return@mapNotNull null
                    if (childBirth.isAfter(motherDeath)) {
                        "${child.displayName()} was born ($childBirth) after the death of their mother " +
                            "${mother.displayName()} ($motherDeath)"
                    } else null
                }
            }.orEmpty()

            fatherIssues + motherIssues
        }
}
