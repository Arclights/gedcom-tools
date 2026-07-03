package com.arclights.consistency

import com.arclights.Gedcom
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Two siblings born too close together to both be biological children of the same parents. */
object SiblingsTooCloseCheck : ConsistencyCheck {
    override val name = "Siblings too close in age"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> {
        val minGapDays = config.minSiblingGapMonths * 30L
        return gedcom.familyGroups.values.flatMap { family ->
            gedcom.children(family)
                .mapNotNull { child -> child.birthDate()?.let { child to it } }
                .sortedBy { (_, birth) -> birth }
                .zipWithNext()
                .mapNotNull { (first, second) ->
                    val gap = ChronoUnit.DAYS.between(first.second, second.second)
                    if (gap > config.twinGraceDays && gap < minGapDays) {
                        "Siblings ${first.first.displayName()} (${first.second}) and " +
                            "${second.first.displayName()} (${second.second}) are only $gap days apart, " +
                            "too close to be biological siblings"
                    } else null
                }
        }
    }
}

/** Spouses with an implausibly large age gap. */
object SpouseAgeGapCheck : ConsistencyCheck {
    override val name = "Large age gap between spouses"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.familyGroups.values.mapNotNull { family ->
            val husband = gedcom.father(family) ?: return@mapNotNull null
            val wife = gedcom.mother(family) ?: return@mapNotNull null
            val husbandBirth = husband.birthDate() ?: return@mapNotNull null
            val wifeBirth = wife.birthDate() ?: return@mapNotNull null
            val gap = abs(yearsBetween(husbandBirth, wifeBirth))
            if (gap > config.maxSpouseAgeGapYears) {
                "Spouses ${husband.displayName()} and ${wife.displayName()} have an age gap of $gap years " +
                    "(maximum ${config.maxSpouseAgeGapYears})"
            } else null
        }
}

/** A spouse younger than the configured minimum at the time of the marriage. */
object MarriedTooYoungCheck : ConsistencyCheck {
    override val name = "Married too young"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.familyGroups.values.flatMap { family ->
            val marriage = family.marriageDate() ?: return@flatMap emptyList()
            gedcom.parents(family).mapNotNull { spouse ->
                val birth = spouse.birthDate() ?: return@mapNotNull null
                val age = yearsBetween(birth, marriage)
                if (age in 0 until config.minMarriageAge) {
                    "${spouse.displayName()} was only $age years old at their marriage " +
                        "(minimum ${config.minMarriageAge})"
                } else null
            }
        }
}

/** The same couple recorded with more than one marriage. */
object MultipleMarriagesCheck : ConsistencyCheck {
    override val name = "Several marriages for the same couple"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> =
        gedcom.familyGroups.values
            .filter { it.husbandId != null && it.wifeId != null }
            .groupBy { setOfNotNull(it.husbandId, it.wifeId) }
            .mapNotNull { (_, families) ->
                val familyCount = families.size
                val marriageEventCount = families.sumOf { it.marriageEvents().size }
                val count = maxOf(familyCount, marriageEventCount)
                if (familyCount > 1 || marriageEventCount > 1) {
                    val couple = families.first()
                    val husband = gedcom.father(couple)?.displayName() ?: couple.husbandId?.value
                    val wife = gedcom.mother(couple)?.displayName() ?: couple.wifeId?.value
                    "$husband and $wife have $count marriages recorded for the same couple"
                } else null
            }
}

/** A person with no relatives anywhere in the tree. */
object UnconnectedPersonCheck : ConsistencyCheck {
    override val name = "Person not connected to the tree"
    override val severity = Severity.WARNING

    override fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String> {
        val connected = buildSet {
            gedcom.familyGroups.values.forEach { family ->
                val members = (family.parentIds() + family.childrenIds).filter(gedcom.individuals::containsKey)
                if (members.size >= 2) addAll(members)
            }
        }
        return gedcom.individuals.values
            .filterNot { it.id in connected }
            .map { "${it.displayName()} is not connected to anyone else in the tree" }
    }
}
