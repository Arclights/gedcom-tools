package com.arclights

import java.time.LocalDate

data class Gedcom(
    val individuals: Map<IndividualId, Individual> = mapOf(),
    val familyGroups: Map<FamilyGroupId, FamilyGroup> = mapOf(),
    val sources: Map<SourceId, Source> = mapOf()
)

data class Individual(
    val id: IndividualId,
    val names: List<IndividualName> = listOf(),
    val sex: Sex? = null,
    val events: List<IndividualEvent> = listOf(),
    val attributes: List<IndividualAttribute> = listOf(),
    val childToFamilies: List<ChildToFamilyLink> = listOf(),
    val spouseToFamilies: List<SpouseToFamilyLink> = listOf(),
    val associations: List<Association> = listOf(),
    val changeDate: LocalDate? = null,
    val notes: List<String> = listOf(),
    val sourceCitations: List<SourceCitation> = listOf(),
    val multimediaLinks: List<MultimediaLink> = listOf()
) {
    override fun toString(): String {
        return "${id.value} ${names.first().name}"
    }
}

data class IndividualName(
    val name: String,
    val type: String? = null,
    val prefix: String? = null,
    val given: String? = null,
    val nickname: String? = null,
    val surnamePrefix: String? = null,
    val surname: String? = null,
    val suffix: String? = null,
    val notes: List<String> = listOf(),
    val sourceCitations: List<SourceCitation> = listOf()
)

interface IndividualAttribute // Not implemented yet

data class ChildToFamilyLink(
    val familyId: FamilyGroupId,
    val pedigreeLinkageType: String? = null,
    val notes: List<String> = listOf()
)

data class SpouseToFamilyLink(
    val familyId: FamilyGroupId,
    val notes: List<String> = listOf()
)

interface Association // Not implemented yet

interface IndividualEvent

data class BirthEvent(
    val details: IndividualEventDetails? = null,
    val familyId: FamilyGroupId? = null
) : IndividualEvent

data class ChristeningEvent(
    val confirmed: Boolean,
    val details: IndividualEventDetails? = null,
    val familyId: FamilyGroupId? = null
) : IndividualEvent

data class DeathEvent(
    val confirmed: Boolean,
    val details: IndividualEventDetails? = null
) : IndividualEvent

data class GeneralIndividualEvent(
    val type: String,
    val details: IndividualEventDetails
) : IndividualEvent

data class IndividualEventDetails(
    val details: EvenDetail,
    val age: String? // Extend, see AGE_AT_EVENT specification
)

data class FamilyGroup(
    val id: FamilyGroupId,
    val events: List<FamilyEvent> = listOf(),
    val husbandId: IndividualId? = null,
    val wifeId: IndividualId? = null,
    val childrenIds: List<IndividualId> = listOf(),
    val nbrOfChildren: Int? = null,
    val changeDate: LocalDate? = null,
    val notes: List<String> = listOf(),
    val sourceCitations: List<SourceCitation> = listOf(),
    val multimediaLinks: List<MultimediaLink> = listOf()
)

data class SourceCitation(
    val source: SourceId,
    val page: String?,
    val eventTypeCitedFrom: EventTypeCitedFrom?,
    val data: Data?,
    val notes: List<String> = listOf(),
    val multimediaLinks: List<MultimediaLink> = listOf(),
    val qualityAssessment: QUAY?
) {
    data class Data(
        val date: DateValue?,
        val text: String?
    )
}

data class EventTypeCitedFrom(
    val eventType: String,
    val role: String?
)

enum class Sex(val value: String) {
    MALE("M"),
    FEMALE("F"),
    INTERSEX("X"),
    UNKNOWN("U"),
    NOT_RECORDED("N");

    companion object {
        fun fromValue(value: String) = values().firstOrNull { value == it.value }
    }
}

data class FamilyEvent(
    val eventType: FamilyEventType,
    val detail: FamilyEventDetail? = null
)

data class FamilyEventDetail(
    val husbandAge: Int? = null,
    val wifeAge: Int? = null,
    val detail: EvenDetail?
)

data class EvenDetail(
    val type: String? = null,
    val date: DateValue? = null,
    val place: Place? = null,
    val address: Address? = null,
//    val responsibleAgency
//    val religiousAffiliation
    val notes: List<String> = listOf(),
    val sourceCitations: List<SourceCitation> = listOf(),
    val multimediaLinks: List<MultimediaLink> = listOf()
)

data class Place(
    val name: String,
    val longitude: Double? = null,
    val latitude: Double? = null,
    val notes: List<String> = listOf()
)

data class Address(
    val address1: String?,
    val address2: String?,
    val address3: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
    val country: String?,
    val phoneNumbers: List<String> = listOf(),
    val emails: List<String> = listOf(),
    val faxes: List<String> = listOf(),
    val wwws: List<String> = listOf()
)

interface EventType {
    companion object {
        fun fromTagName(tagName: String): EventType? =
            FamilyEventType.fromTagName(tagName) as EventType?
                ?: IndividualEventType.fromTagName(tagName) as EventType?
                ?: AttributeType.fromTagName(tagName) as EventType?

        fun fromTagNameStrict(tagName: String): EventType =
            fromTagName(tagName)
                ?: throw IllegalArgumentException("Invalid event type tag: $tagName")
    }
}

enum class FamilyEventType(val tagName: String) : EventType {
    ANNULMENT("ANUL"),
    CENSUS("CENS"),
    DIVORCE("DIV"),
    DIVORCE_FILED("DIVF"),
    ENGAGEMENT("ENGA"),
    MARRIAGE_BANN("MARB"),
    MARR_CONTRACT("MARC"),
    MARRIAGE("MARR"),
    MARR_LICENSE("MARL");

    companion object {
        fun fromTagName(tagName: String) = values().find { it.tagName == tagName }
        fun fromTagNameStrict(tagName: String) = fromTagName(tagName)
            ?: throw IllegalArgumentException("Invalid family event type tag: $tagName")
    }
}

enum class IndividualEventType(val tagName: String) {
    ;

    companion object {
        fun fromTagName(tagName: String) = IndividualEventType.values().find { it.tagName == tagName }
        fun fromTagNameStrict(tagName: String) = fromTagName(tagName)
            ?: throw IllegalArgumentException("Invalid individual event type tag: $tagName")
    }
}

enum class AttributeType(val tagName: String) {
    ;

    companion object {
        fun fromTagName(tagName: String) = AttributeType.values().find { it.tagName == tagName }
        fun fromTagNameStrict(tagName: String) = fromTagName(tagName)
            ?: throw IllegalArgumentException("Invalid attribute type tag: $tagName")
    }
}

enum class QUAY(val value: Int) {
    UNRELIABLE(0),
    QUESTIONABLE(1),
    SECONDARY(2),
    PRIMARY(3),
    DIRECT(4); // Used by MyHeritage

    companion object {
        fun fromValue(value: Int) = values().firstOrNull { it.value == value }
    }
}

data class Source(
    val id: SourceId,
    val data: Data? = null,
    val author: String? = null,
    val title: String? = null,
    val abbreviation: String? = null,
    val publicationFacts: String? = null,
    val text: String? = null,
//    val sourceRepositoryCitations:List<>
//    val reference
    val automatedRecordId: String? = null,
//    val changeDate
    val notes: List<String> = listOf(),
    val multimediaLinks: List<MultimediaLink> = listOf()
) {
    data class Data(
        val events: List<Event> = listOf(),
        val responsibleAgency: String? = null,
        val notes: List<String> = listOf()
    ) {
        data class Event(
            val type: String,
            val date: DateValue? = null,
            val place: String? = null
        )
    }
}

data class Note(
    val id: String,
    val text: String
)

interface DateValue

data class Date(
    val dateCalendarEscape: Calendars?,
    val dateCalendar: DateCalendar
) : DateValue

interface DateCalendar

data class GregorianCalendar(
    val day: Int? = null,
    val month: Month? = null,
    val year: Year? = null,
    val beforeCommonEra: Boolean = false
) : DateCalendar {
    enum class Month {
        JAN,
        FEB,
        MAR,
        APR,
        MAY,
        JUN,
        JUL,
        AUG,
        SEP,
        OCT,
        NOV,
        DEC
    }

    override fun toString() = listOfNotNull(
        day?.toString(),
        month,
        year?.newStyle?.toString(),
        if (beforeCommonEra) "BC" else null
    ).joinToString(" ")
}

data class Year(
    val newStyle: Int,
    val oldStyle: Int
)

data class DatePeriod(
    val from: Date,
    val to: Date
) : DateValue

interface DateRange : DateValue
data class DateRangeBefore(val date: Date) : DateRange
data class DateRangeAfter(val date: Date) : DateRange
data class DateRangeBetween(val date1: Date, val date2: Date) : DateRange

data class DateApproximated(val date: Date) : DateValue

data class DatePhrase(val text: String) : DateValue

data class DatePhraseExt(
    val int: Int,
    val date: Date,
    val datePhrase: DatePhrase
) : DateValue

enum class Calendars(val id: String) {
    HEBREW("@#DHEBREW@"),
    FRENCH("@#DFRENCH R@"),
    GREGORIAN("@#DGREGORIAN@"),
    JULIAN("@#DJULIAN@"),
    UNKNOWN("@#DUNKNOWN@")
}

@JvmInline
value class IndividualId(val value: String)

@JvmInline
value class FamilyGroupId(val value: String)

@JvmInline
value class SourceId(val value: String)

@JvmInline
value class MultimediaLink(val value: String)