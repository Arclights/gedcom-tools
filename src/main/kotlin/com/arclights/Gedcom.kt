package com.arclights

import java.time.LocalDate

data class Gedcom(
    val header: Header? = null,
    val individuals: Map<IndividualId, Individual> = mapOf(),
    val familyGroups: Map<FamilyGroupId, FamilyGroup> = mapOf(),
    val sources: Map<SourceId, Source> = mapOf(),
    val repositories: Map<String, Repository> = mapOf(),
    val noteRecords: Map<String, NoteRecord> = mapOf(),
    val multimediaRecords: Map<String, MultimediaRecord> = mapOf(),
    val submitters: Map<String, Submitter> = mapOf(),
    val submissions: Map<String, Submission> = mapOf()
)

data class Individual(
    val id: IndividualId,
    val names: List<IndividualName> = listOf(),
    val sex: Sex? = null,
    val events: List<IndividualEvent> = listOf(),
    val attributes: List<IndividualAttribute> = listOf(),
    val ldsOrdinances: List<LdsOrdinance> = listOf(),
    val childToFamilies: List<ChildToFamilyLink> = listOf(),
    val spouseToFamilies: List<SpouseToFamilyLink> = listOf(),
    val associations: List<Association> = listOf(),
    val restriction: String? = null,
    val aliases: List<IndividualId> = listOf(),
    val ancestorInterestSubmitterIds: List<String> = listOf(),
    val descendantInterestSubmitterIds: List<String> = listOf(),
    val submitterIds: List<String> = listOf(),
    val permanentRecordFileNumber: String? = null,
    val ancestralFileNumber: String? = null,
    val references: List<String> = listOf(),
    val automatedRecordId: String? = null,
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
    val phoneticVariations: List<PersonalNameVariation> = listOf(),
    val romanizedVariations: List<PersonalNameVariation> = listOf(),
    val notes: List<String> = listOf(),
    val sourceCitations: List<SourceCitation> = listOf()
)

// A FONE (phonetic) or ROMN (romanized) rendering of a personal name. [method] carries the
// required TYPE sub-value describing the phonetic system or romanization method used.
data class PersonalNameVariation(
    val name: String,
    val method: String? = null,
    val prefix: String? = null,
    val given: String? = null,
    val nickname: String? = null,
    val surnamePrefix: String? = null,
    val surname: String? = null,
    val suffix: String? = null,
    val notes: List<String> = listOf(),
    val sourceCitations: List<SourceCitation> = listOf()
)

interface IndividualAttribute

data class GeneralIndividualAttribute(
    val type: String,
    val value: String,
    val detail: EventDetail
) : IndividualAttribute

data class ChildToFamilyLink(
    val familyId: FamilyGroupId,
    val pedigreeLinkageType: String? = null,
    val notes: List<String> = listOf()
)

data class SpouseToFamilyLink(
    val familyId: FamilyGroupId,
    val notes: List<String> = listOf()
)

data class Association(
    val individualId: IndividualId,
    val relation: String? = null,
    val notes: List<String> = listOf(),
    val sourceCitations: List<SourceCitation> = listOf()
)

// An LDS ordinance (BAPL, CONL, ENDL, SLGC for individuals; SLGS for families).
data class LdsOrdinance(
    val type: String,
    val date: DateValue? = null,
    val templeCode: String? = null,
    val place: Place? = null,
    val status: String? = null,
    val familyId: FamilyGroupId? = null,
    val notes: List<String> = listOf(),
    val sourceCitations: List<SourceCitation> = listOf()
)

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
    val details: IndividualEventDetails? = null,
    val familyId: FamilyGroupId? = null
) : IndividualEvent

data class IndividualEventDetails(
    val details: EventDetail,
    val age: String? // Extend, see AGE_AT_EVENT specification
)

data class FamilyGroup(
    val id: FamilyGroupId,
    val events: List<FamilyEvent> = listOf(),
    val husbandId: IndividualId? = null,
    val wifeId: IndividualId? = null,
    val childrenIds: List<IndividualId> = listOf(),
    val nbrOfChildren: Int? = null,
    val ldsSpouseSealings: List<LdsOrdinance> = listOf(),
    val submitterIds: List<String> = listOf(),
    val restriction: String? = null,
    val references: List<String> = listOf(),
    val automatedRecordId: String? = null,
    val changeDate: LocalDate? = null,
    val notes: List<String> = listOf(),
    val sourceCitations: List<SourceCitation> = listOf(),
    val multimediaLinks: List<MultimediaLink> = listOf()
)

data class SourceCitation(
    val source: SourceId? = null,
    // An inline source citation carries its descriptive text here instead of a source pointer.
    val description: String? = null,
    val page: String? = null,
    val eventTypeCitedFrom: EventTypeCitedFrom? = null,
    val data: Data? = null,
    val notes: List<String> = listOf(),
    val multimediaLinks: List<MultimediaLink> = listOf(),
    val qualityAssessment: QUAY? = null
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
    val husbandAge: String? = null,
    val wifeAge: String? = null,
    val detail: EventDetail?
)

data class EventDetail(
    val type: String? = null,
    val date: DateValue? = null,
    val place: Place? = null,
    val address: Address? = null,
    val responsibleAgency: String? = null,
    val religiousAffiliation: String? = null,
    val cause: String? = null,
    val restriction: String? = null,
    val notes: List<String> = listOf(),
    val sourceCitations: List<SourceCitation> = listOf(),
    val multimediaLinks: List<MultimediaLink> = listOf()
)

data class Place(
    val name: String,
    val form: String? = null,
    val longitude: Double? = null,
    val latitude: Double? = null,
    val phoneticVariations: List<PlaceVariation> = listOf(),
    val romanizedVariations: List<PlaceVariation> = listOf(),
    val notes: List<String> = listOf()
)

// A FONE (phonetic) or ROMN (romanized) rendering of a place name.
data class PlaceVariation(
    val name: String,
    val method: String? = null
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
    MARR_LICENSE("MARL"),
    MARR_SETTLEMENT("MARS"),
    EVENT("EVEN");

    companion object {
        fun fromTagName(tagName: String) = values().find { it.tagName == tagName }
        fun fromTagNameStrict(tagName: String) = fromTagName(tagName)
            ?: throw IllegalArgumentException("Invalid family event type tag: $tagName")
    }
}

enum class IndividualEventType(val tagName: String) : EventType {
    BIRTH("BIRT"),
    CHRISTENING("CHR"),
    DEATH("DEAT"),
    BURIAL("BURI"),
    CREMATION("CREM"),
    ADOPTION("ADOP"),
    BAPTISM("BAPM"),
    BAR_MITZVAH("BARM"),
    BAS_MITZVAH("BASM"),
    BLESSING("BLES"),
    ADULT_CHRISTENING("CHRA"),
    CONFIRMATION("CONF"),
    FIRST_COMMUNION("FCOM"),
    ORDINATION("ORDN"),
    NATURALIZATION("NATU"),
    EMIGRATION("EMIG"),
    IMMIGRATION("IMMI"),
    CENSUS("CENS"),
    PROBATE("PROB"),
    WILL("WILL"),
    GRADUATION("GRAD"),
    RETIREMENT("RETI"),
    EVENT("EVEN");

    companion object {
        fun fromTagName(tagName: String) = values().find { it.tagName == tagName }
        fun fromTagNameStrict(tagName: String) = fromTagName(tagName)
            ?: throw IllegalArgumentException("Invalid individual event type tag: $tagName")
    }
}

enum class AttributeType(val tagName: String) : EventType {
    CASTE("CAST"),
    PHYSICAL_DESCRIPTION("DSCR"),
    EDUCATION("EDUC"),
    IDENTIFICATION_NUMBER("IDNO"),
    NATIONALITY("NATI"),
    CHILDREN_COUNT("NCHI"),
    MARRIAGE_COUNT("NMR"),
    OCCUPATION("OCCU"),
    PROPERTY("PROP"),
    RELIGION("RELI"),
    RESIDENCE("RESI"),
    TITLE("TITL"),
    FACT("FACT");

    companion object {
        fun fromTagName(tagName: String) = values().find { it.tagName == tagName }
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
    val repositoryCitations: List<SourceRepositoryCitation> = listOf(),
    val references: List<String> = listOf(),
    val automatedRecordId: String? = null,
    val changeDate: LocalDate? = null,
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

data class SourceRepositoryCitation(
    val repositoryId: String,
    val callNumbers: List<String> = listOf(),
    val notes: List<String> = listOf()
)

data class NoteRecord(
    val id: String,
    val text: String,
    val sourceCitations: List<SourceCitation> = listOf(),
    val references: List<String> = listOf(),
    val automatedRecordId: String? = null,
    val changeDate: LocalDate? = null
)

data class Repository(
    val id: String,
    val name: String? = null,
    val address: Address? = null,
    val references: List<String> = listOf(),
    val automatedRecordId: String? = null,
    val notes: List<String> = listOf(),
    val changeDate: LocalDate? = null
)

data class Submitter(
    val id: String,
    val name: String? = null,
    val address: Address? = null,
    val languages: List<String> = listOf(),
    val registeredFileNumber: String? = null,
    val automatedRecordId: String? = null,
    val multimediaLinks: List<MultimediaLink> = listOf(),
    val notes: List<String> = listOf(),
    val changeDate: LocalDate? = null
)

data class Submission(
    val id: String,
    val submitterId: String? = null,
    val familyFile: String? = null,
    val templeCode: String? = null,
    val ancestorGenerations: Int? = null,
    val descendantGenerations: Int? = null,
    val ordinanceProcessFlag: String? = null,
    val automatedRecordId: String? = null,
    val notes: List<String> = listOf()
)

data class Header(
    val source: Source? = null,
    val destination: String? = null,
    val date: LocalDate? = null,
    val time: String? = null,
    val submitterId: String? = null,
    val submissionId: String? = null,
    val fileName: String? = null,
    val copyright: String? = null,
    val gedcomVersion: String? = null,
    val gedcomForm: String? = null,
    val characterSet: String? = null,
    val characterSetVersion: String? = null,
    val language: String? = null,
    val placeForm: String? = null,
    val notes: List<String> = listOf()
) {
    data class Source(
        val systemId: String,
        val version: String? = null,
        val productName: String? = null,
        val corporation: String? = null
    )
}

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

// The Julian calendar shares the Gregorian month names but is a distinct calendar for
// dates recorded under @#DJULIAN@.
data class JulianCalendar(
    val day: Int? = null,
    val month: GregorianCalendar.Month? = null,
    val year: Int? = null,
    val beforeCommonEra: Boolean = false
) : DateCalendar {
    override fun toString() = listOfNotNull(
        day?.toString(),
        month,
        year?.toString(),
        if (beforeCommonEra) "BC" else null
    ).joinToString(" ")
}

data class HebrewCalendar(
    val day: Int? = null,
    val month: Month? = null,
    val year: Int? = null
) : DateCalendar {
    enum class Month {
        TSH, CSH, KSL, TVT, SHV, ADR, ADS, NSN, IYR, SVN, TMZ, AAV, ELL
    }

    override fun toString() = listOfNotNull(day?.toString(), month, year?.toString()).joinToString(" ")
}

data class FrenchCalendar(
    val day: Int? = null,
    val month: Month? = null,
    val year: Int? = null
) : DateCalendar {
    enum class Month {
        VEND, BRUM, FRIM, NIVO, PLUV, VENT, GERM, FLOR, PRAI, MESS, THER, FRUC, COMP
    }

    override fun toString() = listOfNotNull(day?.toString(), month, year?.toString()).joinToString(" ")
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

// An interpreted date: `INT <date> (<phrase>)`, where [phrase] is the free text the
// interpreted [date] was derived from.
data class DatePhraseExt(
    val date: Date,
    val phrase: String
) : DateValue

enum class Calendars(val id: String) {
    HEBREW("@#DHEBREW@"),
    FRENCH("@#DFRENCH R@"),
    GREGORIAN("@#DGREGORIAN@"),
    JULIAN("@#DJULIAN@"),
    UNKNOWN("@#DUNKNOWN@");

    companion object {
        fun fromId(id: String) = values().firstOrNull { it.id == id }
    }
}

@JvmInline
value class IndividualId(val value: String)

@JvmInline
value class FamilyGroupId(val value: String)

@JvmInline
value class SourceId(val value: String)

sealed interface MultimediaLink

// A multimedia link that points to a level-0 OBJE record by its xref id.
data class MultimediaReference(val id: String) : MultimediaLink

// A multimedia link that embeds the object inline rather than pointing to a record.
data class EmbeddedMultimedia(
    val files: List<MultimediaFile> = listOf(),
    val title: String? = null
) : MultimediaLink

data class MultimediaFile(
    val reference: String,
    val format: String? = null,
    val mediaType: String? = null,
    val title: String? = null
)

data class MultimediaRecord(
    val id: String,
    val files: List<MultimediaFile> = listOf(),
    val title: String? = null,
    val references: List<String> = listOf(),
    val automatedRecordId: String? = null,
    val notes: List<String> = listOf(),
    val sourceCitations: List<SourceCitation> = listOf(),
    val changeDate: LocalDate? = null
)
