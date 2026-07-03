package com.arclights.consistency

import com.arclights.Gedcom
import java.time.LocalDate

enum class Severity {
    ERROR,
    WARNING
}

/** A single detected inconsistency, tagged with the check that produced it and its severity. */
data class Issue(
    val severity: Severity,
    val check: String,
    val message: String
)

/**
 * Tunable thresholds for the checks that the user wants to configure. Callers can override any of
 * these; [today] is injectable so "in the future" and "alive but too old" checks are deterministic
 * in tests.
 */
data class ConsistencyConfig(
    /** Maximum plausible age at death. */
    val maxDeathAge: Int = 110,
    /** Maximum plausible age for a person with no recorded death. */
    val maxLivingAge: Int = 110,
    /** Youngest a parent may be when a child is born. */
    val minParentAge: Int = 13,
    /** Oldest a parent may be when a child is born. */
    val maxParentAge: Int = 70,
    /** Minimum spacing between two (non-twin) biological siblings, in months. */
    val minSiblingGapMonths: Int = 8,
    /** Births within this many days of each other are treated as twins and never flagged. */
    val twinGraceDays: Int = 2,
    /** Largest plausible age gap between spouses, in years. */
    val maxSpouseAgeGapYears: Int = 40,
    /** Youngest a person may be at their own marriage. */
    val minMarriageAge: Int = 16,
    /** Grace period after a father's death during which a child may still be born (gestation). */
    val fatherPosthumousGraceDays: Int = 300,
    /** "Today", used by the future-date and living-age checks. */
    val today: LocalDate = LocalDate.now()
)

/**
 * A single, self contained data-quality check. Implementations produce human readable findings; the
 * runner attaches the [severity] and [name] to build [Issue]s. To add a new check, implement this
 * interface (typically as an `object`) and add it to [consistencyChecks].
 */
interface ConsistencyCheck {
    val name: String
    val severity: Severity
    fun findings(gedcom: Gedcom, config: ConsistencyConfig): List<String>
}

/** The registry of all checks. Add new checks here to have them picked up by the command. */
val consistencyChecks: List<ConsistencyCheck> = listOf(
    // Errors
    DeathBeforeBirthCheck,
    ChildOlderThanParentCheck,
    ChildBornAfterParentDeathCheck,
    // Warnings
    DiedTooOldCheck,
    AliveTooOldCheck,
    ParentTooYoungCheck,
    ParentTooOldCheck,
    EventBeforeBirthCheck,
    EventAfterDeathCheck,
    SiblingsTooCloseCheck,
    SpouseAgeGapCheck,
    MarriedTooYoungCheck,
    MultipleMarriagesCheck,
    MarriedNameAsMaidenCheck,
    TitleInGivenNameCheck,
    TitleInSurnameCheck,
    MultipleBirthEventsCheck,
    MultipleDeathEventsCheck,
    FutureDateCheck,
    UnconnectedPersonCheck
)

/** Runs every registered check and returns all issues found, most severe first. */
fun runConsistencyChecks(gedcom: Gedcom, config: ConsistencyConfig = ConsistencyConfig()): List<Issue> =
    consistencyChecks
        .flatMap { check -> check.findings(gedcom, config).map { Issue(check.severity, check.name, it) } }
        .sortedBy { it.severity }
