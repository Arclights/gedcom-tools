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
    val sex: Sex?,
    val events: List<IndividualEvent> = listOf(),
    val attributes: List<IndividualAttribute> = listOf(),
    val childToFamilies: List<ChildToFamilyLink> = listOf(),
    val spouseToFamilies: List<SpouseToFamilyLink> = listOf(),
    val associations: List<Association> = listOf(),
    val changeDate: LocalDate?,
    val notes: List<String> = listOf(),
    val sourceCitations: List<SourceCitation> = listOf(),
    val multimediaLinks: List<MultimediaLink> = listOf()
)

data class IndividualName(
    val name: String,
    val type: String?,
    val prefix: String?,
    val given: String?,
    val nickname: String?,
    val surnamePrefix: String?,
    val surname: String?,
    val suffix: String?,
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
    val details: IndividualEventDetails,
    val familyId: FamilyGroupId
) : IndividualEvent

data class ChristeningEvent(
    val details: IndividualEventDetails,
    val familyId: FamilyGroupId
) : IndividualEvent

data class GeneralIndividualEvent(
    val type: IndividualEventType,
    val details: IndividualEventDetails
) : IndividualEvent

data class IndividualEventDetails(
    val details: EvenDetail,
    val age: Int
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
        val date: LocalDate?,
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
    val date: LocalDate? = null,
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

enum class QUAY(private val value: Int) {
    UNRELIABLE(0),
    QUESTIONABLE(1),
    SECONDARY(2),
    PRIMARY(3);

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
            val date: LocalDate? = null,
            val place: String? = null
        )
    }
}

data class Note(
    val id: String,
    val text: String
)

@JvmInline
value class IndividualId(val value: String)

@JvmInline
value class FamilyGroupId(val value: String)

@JvmInline
value class SourceId(val value: String)

@JvmInline
value class MultimediaLink(val value: String)