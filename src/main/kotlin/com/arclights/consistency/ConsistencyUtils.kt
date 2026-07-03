package com.arclights.consistency

import com.arclights.BirthEvent
import com.arclights.ChristeningEvent
import com.arclights.Date
import com.arclights.DateApproximated
import com.arclights.DateCalendar
import com.arclights.DatePeriod
import com.arclights.DatePhrase
import com.arclights.DatePhraseExt
import com.arclights.DateRangeAfter
import com.arclights.DateRangeBefore
import com.arclights.DateRangeBetween
import com.arclights.DateValue
import com.arclights.DeathEvent
import com.arclights.FamilyEvent
import com.arclights.FamilyEventType
import com.arclights.FamilyGroup
import com.arclights.Gedcom
import com.arclights.GeneralIndividualEvent
import com.arclights.GregorianCalendar
import com.arclights.Individual
import com.arclights.IndividualEvent
import com.arclights.IndividualId
import com.arclights.IndividualName
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Helpers shared by the consistency checks for turning the rich GEDCOM model into the
 * simple, comparable values (dates, ages, name parts) that most checks reason about.
 */

/**
 * Best-effort single [LocalDate] used for comparisons. Missing month/day components default to the
 * start of their period, and imprecise dates (ranges, periods, approximations) collapse to a
 * representative point. Returns `null` when no calendar date can be derived (e.g. a free-text phrase).
 */
fun DateValue.toLocalDateOrNull(): LocalDate? = when (this) {
    is Date -> dateCalendar.toLocalDateOrNull()
    is DateApproximated -> date.toLocalDateOrNull()
    is DatePeriod -> from.toLocalDateOrNull()
    is DateRangeBefore -> date.toLocalDateOrNull()
    is DateRangeAfter -> date.toLocalDateOrNull()
    is DateRangeBetween -> date1.toLocalDateOrNull()
    is DatePhraseExt -> date.toLocalDateOrNull()
    is DatePhrase -> null
    else -> null
}

private fun DateCalendar.toLocalDateOrNull(): LocalDate? = when (this) {
    is GregorianCalendar -> {
        val newStyleYear = year?.newStyle ?: return null
        val yearNumber = if (beforeCommonEra) -newStyleYear else newStyleYear
        runCatching {
            LocalDate.of(yearNumber, (month?.ordinal ?: 0) + 1, day ?: 1)
        }.getOrNull()
    }

    else -> null
}

/** A short human readable label for an event, used when describing an issue. */
fun IndividualEvent.label(): String = when (this) {
    is BirthEvent -> "Birth"
    is ChristeningEvent -> "Christening"
    is DeathEvent -> "Death"
    is GeneralIndividualEvent -> type
    else -> this::class.simpleName ?: "Event"
}

fun IndividualEvent.dateValue(): DateValue? = when (this) {
    is BirthEvent -> details?.details?.date
    is ChristeningEvent -> details?.details?.date
    is DeathEvent -> details?.details?.date
    is GeneralIndividualEvent -> details.details.date
    else -> null
}

fun IndividualEvent.date(): LocalDate? = dateValue()?.toLocalDateOrNull()

fun Individual.birthEvents(): List<BirthEvent> = events.filterIsInstance<BirthEvent>()
fun Individual.deathEvents(): List<DeathEvent> = events.filterIsInstance<DeathEvent>()

fun Individual.birthDate(): LocalDate? = birthEvents().firstNotNullOfOrNull(BirthEvent::date)
fun Individual.deathDate(): LocalDate? = deathEvents().firstNotNullOfOrNull(DeathEvent::date)

fun Individual.displayName(): String = names.firstOrNull()?.name ?: id.value

fun FamilyEvent.date(): LocalDate? = detail?.detail?.date?.toLocalDateOrNull()

fun FamilyGroup.marriageEvents(): List<FamilyEvent> = events.filter { it.eventType == FamilyEventType.MARRIAGE }
fun FamilyGroup.marriageDate(): LocalDate? = marriageEvents().firstNotNullOfOrNull(FamilyEvent::date)

fun FamilyGroup.parentIds(): List<IndividualId> = listOfNotNull(husbandId, wifeId)
fun Gedcom.father(family: FamilyGroup): Individual? = family.husbandId?.let(individuals::get)
fun Gedcom.mother(family: FamilyGroup): Individual? = family.wifeId?.let(individuals::get)
fun Gedcom.parents(family: FamilyGroup): List<Individual> = family.parentIds().mapNotNull(individuals::get)
fun Gedcom.children(family: FamilyGroup): List<Individual> = family.childrenIds.mapNotNull(individuals::get)

/** Full years elapsed between two dates; negative when [to] precedes [from]. */
fun yearsBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.YEARS.between(from, to)

/** The given (first) name, falling back to parsing the raw `GIVEN /SURNAME/` string. */
fun IndividualName.givenName(): String? =
    given ?: name.substringBefore("/").trim().ifEmpty { null }

private val surnameRegex = "/([^/]*)/".toRegex()

/** The surname, falling back to the value between the slashes of the raw name string. */
fun IndividualName.surnameValue(): String? =
    surname ?: surnameRegex.find(name)?.groupValues?.get(1)?.trim()?.ifEmpty { null }

/**
 * Titles that should live in a dedicated title field rather than inside a person's given name or
 * surname. Matched case-insensitively against whitespace separated tokens.
 */
val TITLES: Set<String> = setOf(
    "dr", "mr", "mrs", "ms", "miss", "sir", "lord", "lady", "dame", "rev", "reverend",
    "capt", "captain", "col", "colonel", "gen", "general", "maj", "major", "sgt", "sergeant",
    "lt", "lieutenant", "prof", "professor", "hon", "honorable", "pastor", "bishop", "cardinal",
    "king", "queen", "prince", "princess", "count", "countess", "baron", "baroness", "duke", "duchess"
)

/**
 * The title tokens found inside [namePart]. A title is only reported when the name part has more than
 * one token (e.g. "Dr John"), so that legitimate standalone surnames such as "King" are not flagged.
 */
fun titlesIn(namePart: String): List<String> {
    val tokens = namePart.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (tokens.size < 2) return emptyList()
    return tokens.filter { it.trim('.', ',').lowercase() in TITLES }
}
