package com.arclights

import org.slf4j.LoggerFactory
import java.time.LocalDate

private val logger = LoggerFactory.getLogger("GedcomParser")

fun parseGedcom(lines: List<String>): Gedcom {
    val lineIterator = lines
        .let(::stripByteOrderMark)
        .let(::insertDummyFirstLine)
        .let(::mergeOrphanedLines)
        .mapIndexed(::Line)
        .also(::validateFormat)
        .mergeConcAndCont()
        .lineIterator()

    val parseContainer = ParseContainer()

    logger.debug("parsing")
    lineIterator.parseByContent(
        ContentParser("FAM") { id ->
            parseFamilyGroup(id, lineIterator).let { parseContainer.familyGroups.add(it) }
        },
        ContentParser("INDI") { id ->
            parseIndividual(id, lineIterator).let { parseContainer.individuals.add(it) }
        },
        ContentParser("SOUR") { id ->
            parseSource(id, lineIterator).let { parseContainer.sources.add(it) }
        },
        // These level-0 records are valid GEDCOM but not consumed by this tool. They
        // are skipped like any unrecognized record, just without a warning per file.
        // HEAD/TRLR are tag-only lines (no xref) so their "content" is empty.
        knownUnhandled = setOf("", "SUBM", "SUBN", "REPO", "NOTE", "OBJE")
    )

    logger.debug("parsed")
    logger.debug("Nbr of family groups: ${parseContainer.familyGroups.size}")
    logger.debug("Nbr of individuals: ${parseContainer.individuals.size}")
    logger.debug("Nbr of sources: ${parseContainer.sources.size}")
    return Gedcom(
        familyGroups = parseContainer.familyGroups.associateByLoggingDuplicates(FamilyGroup::id),
        individuals = parseContainer.individuals.associateByLoggingDuplicates(Individual::id),
        sources = parseContainer.sources.associateByLoggingDuplicates(Source::id)
    ).also(Gedcom::logDanglingReferences)
}

private fun Gedcom.logDanglingReferences() {
    individuals.values.forEach { individual ->
        individual.childToFamilies.forEach { link ->
            if (link.familyId !in familyGroups) {
                logger.warn("Individual '${individual.id.value}' references unknown family group '${link.familyId.value}' via FAMC")
            }
        }
        individual.spouseToFamilies.forEach { link ->
            if (link.familyId !in familyGroups) {
                logger.warn("Individual '${individual.id.value}' references unknown family group '${link.familyId.value}' via FAMS")
            }
        }
    }

    familyGroups.values.forEach { family ->
        family.husbandId?.let {
            if (it !in individuals) {
                logger.warn("Family group '${family.id.value}' references unknown individual '${it.value}' via HUSB")
            }
        }
        family.wifeId?.let {
            if (it !in individuals) {
                logger.warn("Family group '${family.id.value}' references unknown individual '${it.value}' via WIFE")
            }
        }
        family.childrenIds.forEach {
            if (it !in individuals) {
                logger.warn("Family group '${family.id.value}' references unknown individual '${it.value}' via CHIL")
            }
        }
    }
}

private fun <T, K> Collection<T>.associateByLoggingDuplicates(keySelector: (T) -> K): Map<K, T> {
    val result = LinkedHashMap<K, T>(size)
    for (element in this) {
        val key = keySelector(element)
        if (result.containsKey(key)) {
            logger.warn("Duplicate id '$key' found, discarding the earlier record with this id")
        }
        result[key] = element
    }
    return result
}

private fun validateFormat(lines: List<Line>) {
    val improperlyFormattedLines = lines.filter { it.inProperFormat().not() }

    if (improperlyFormattedLines.isEmpty().not()) {
        val message = improperlyFormattedLines.joinToString(
            "\n",
            "Following lines are improperly formatted:\n",
            "\nCannot parse file"
        )
        throw IllegalArgumentException(message)
    }
}

private fun List<Line>.mergeConcAndCont(): List<Line> {
    val merged = mutableListOf<Line>()
    for (line in this) {
        when (line.tag()) {
            "CONC" -> merged[merged.lastIndex] = merged.last().let { it.copy(line = it.line + line.content()) }
            "CONT" -> merged[merged.lastIndex] = merged.last().let { it.copy(line = it.line + "\n" + line.content()) }
            else -> merged.add(line)
        }
    }
    return merged
}

private fun parseIndividual(id: String, lineIterator: LineIterator): Individual {

    val names = mutableListOf<IndividualName>()
    var sex: Sex? = null
    val events = mutableListOf<IndividualEvent>()
    val attributes = mutableListOf<IndividualAttribute>()
    val childToFamilies = mutableListOf<ChildToFamilyLink>()
    val spouseToFamilies = mutableListOf<SpouseToFamilyLink>()
    val associations = mutableListOf<Association>()
    var changeDate: LocalDate? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    val attributeParser: (String) -> Unit = { value ->
        parseIndividualAttribute(lineIterator.current().tag(), value, lineIterator).let(attributes::add)
    }

    lineIterator.parseByTag(
        TagParser("NAME") { name -> parsePersonalName(name, lineIterator).let(names::add) },
        TagParser("SEX") { sexValue ->
            sex = Sex.fromValue(sexValue)
            if (sex == null) {
                logger.warn("Unrecognized SEX value '$sexValue' for individual '$id'")
            }
        },
        noteParser(notes),
        multimediaLinkParser(multimediaLinks),
        sourceCitationParser(sourceCitations, lineIterator),
        TagParser("BIRT") { events.add(parseBirthEvent(lineIterator)) },
        TagParser("DEAT") { events.add(parseDeathEvent(parseConfirmed(it, "death event of '$id'"), lineIterator)) },
        TagParser("CHR") {
            events.add(parseChristeningEvent(parseConfirmed(it, "christening event of '$id'"), lineIterator))
        },
//                "BURI",
//                "CREM",
//                "ADOP",
//                "BAPM",
//                "BARM",
//                "BASM",
//                "CHRA",
//                "CONF",
//                "FCOM",
//                "NATU",
//                "EMIG",
//                "IMMI",
//                "CENS",
//                "PROB",
//                "WILL",
//                "GRAD",
//                "RETI"-> parseevents
        TagParser("CAST", attributeParser),
        TagParser("DSCR", attributeParser),
        TagParser("EDUC", attributeParser),
        TagParser("IDNO", attributeParser),
        TagParser("NATI", attributeParser),
        TagParser("NCHI", attributeParser),
        TagParser("NMR", attributeParser),
        TagParser("OCCU", attributeParser),
        TagParser("PROP", attributeParser),
        TagParser("RELI", attributeParser),
        TagParser("RESI", attributeParser),
        TagParser("TITL", attributeParser),
        TagParser("FACT", attributeParser),
        TagParser("FAMC") { familyId ->
            parseChildToFamilyLink(
                familyId,
                lineIterator
            ).let(childToFamilies::add)
        },
        TagParser("FAMS") { familyId -> parseSpouseToFamilyLink(familyId, lineIterator).let(spouseToFamilies::add) },
        TagParser("ASSO") { individualId -> parseAssociation(individualId, lineIterator).let(associations::add) },
        TagParser("CHAN") { changeDate = parseChangeDate(lineIterator) }
    )

    return Individual(
        IndividualId(id),
        names,
        sex,
        events,
        attributes,
        childToFamilies,
        spouseToFamilies,
        associations,
        changeDate,
        notes,
        sourceCitations,
        multimediaLinks
    )
}

private fun parseIndividualAttribute(type: String, value: String, lineIterator: LineIterator): GeneralIndividualAttribute {
    val detail = EventDetailAccumulator()

    lineIterator.parseByTag(*detail.tagParsers(lineIterator))

    return GeneralIndividualAttribute(type, value, detail.build())
}

private fun parseAssociation(individualId: String, lineIterator: LineIterator): Association {
    var relation: String? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()

    lineIterator.parseByTag(
        TagParser("RELA") { relation = it },
        noteParser(notes),
        sourceCitationParser(sourceCitations, lineIterator)
    )

    return Association(
        IndividualId(individualId),
        relation,
        notes,
        sourceCitations
    )
}

private val changeDateRegex =
    """^(\d{1,2}) (JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})$""".toRegex()

private fun parseChangeDate(lineIterator: LineIterator): LocalDate? {
    var changeDate: LocalDate? = null

    lineIterator.parseByTag(
        TagParser("DATE") { changeDate = parseSimpleGregorianDate(it) }
    )

    return changeDate
}

private fun parseSimpleGregorianDate(dateString: String): LocalDate? =
    changeDateRegex.matchEntire(dateString)?.destructured?.let { (day, month, year) ->
        LocalDate.of(year.toInt(), GregorianCalendar.Month.valueOf(month).ordinal + 1, day.toInt())
    }

private fun parsePersonalName(name: String, lineIterator: LineIterator): IndividualName {
    var type: String? = null
    var prefix: String? = null
    var given: String? = null
    var nickname: String? = null
    var surnamePrefix: String? = null
    var surname: String? = null
    var suffix: String? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()

    lineIterator.parseByTag(
        TagParser("TYPE") { type = it },
        TagParser("NPFX") { prefix = it },
        TagParser("GIVN") { given = it },
        TagParser("NICK") { nickname = it },
        TagParser("SPFX") { surnamePrefix = it },
        TagParser("SURN") { surname = it },
        TagParser("NSFX") { suffix = it },
        noteParser(notes),
        sourceCitationParser(sourceCitations, lineIterator)
    )

    return IndividualName(
        name,
        type,
        prefix,
        given,
        nickname,
        surnamePrefix,
        surname,
        suffix,
        notes,
        sourceCitations
    )
}

private fun parseChildToFamilyLink(familyId: String, lineIterator: LineIterator): ChildToFamilyLink {
    var pedigreeLinkageType: String? = null
    val notes = mutableListOf<String>()

    lineIterator.parseByTag(
        TagParser("PEDI") { pedigreeLinkageType = it },
        noteParser(notes)
    )

    return ChildToFamilyLink(
        FamilyGroupId(familyId),
        pedigreeLinkageType,
        notes.toList()
    )
}

private fun parseSpouseToFamilyLink(familyId: String, lineIterator: LineIterator): SpouseToFamilyLink {
    val notes = mutableListOf<String>()

    lineIterator.parseByTag(
        noteParser(notes)
    )

    return SpouseToFamilyLink(
        FamilyGroupId(familyId),
        notes.toList()
    )
}

private fun parseBirthEvent(lineIterator: LineIterator): BirthEvent {
    var familyId: FamilyGroupId? = null
    val detail = IndividualEventDetailAccumulator()

    lineIterator.parseByTag(
        TagParser("FAMC") { familyId = FamilyGroupId(it) },
        *detail.tagParsers(lineIterator)
    )

    return BirthEvent(detail.build(), familyId)
}

private fun parseDeathEvent(confirmed: Boolean, lineIterator: LineIterator): DeathEvent {
    val detail = IndividualEventDetailAccumulator()

    lineIterator.parseByTag(*detail.tagParsers(lineIterator))

    return DeathEvent(confirmed, detail.build())
}

private fun parseChristeningEvent(confirmed: Boolean, lineIterator: LineIterator): ChristeningEvent {
    var familyId: FamilyGroupId? = null
    val detail = IndividualEventDetailAccumulator()

    lineIterator.parseByTag(
        TagParser("FAMC") { familyId = FamilyGroupId(it) },
        *detail.tagParsers(lineIterator)
    )

    return ChristeningEvent(confirmed, detail.build(), familyId)
}

private fun parseFamilyGroup(id: String, lineIterator: LineIterator): FamilyGroup {
    val events = mutableListOf<FamilyEvent>()
    var husband: IndividualId? = null
    var wife: IndividualId? = null
    val children = mutableListOf<IndividualId>()
    var nbrOfChildren: Int? = null
    var changeDate: LocalDate? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    val familyEventParser: (String) -> Unit = { _ -> parseFamilyEvent(lineIterator).let { events.add(it) } }

    lineIterator.parseByTag(
        TagParser("HUSB") { husband = IndividualId(it) },
        TagParser("WIFE") { wife = IndividualId(it) },
        TagParser("CHIL") { children.add(IndividualId(it)) },
        TagParser("NCHI") { nbrOfChildren = it.toInt() },
        TagParser("ANUL", familyEventParser),
        TagParser("CENS", familyEventParser),
        TagParser("DIV", familyEventParser),
        TagParser("DIVF", familyEventParser),
        TagParser("ENGA", familyEventParser),
        TagParser("MARB", familyEventParser),
        TagParser("MARC", familyEventParser),
        TagParser("MARR", familyEventParser),
        TagParser("MARL", familyEventParser),
        TagParser("MARS", familyEventParser),
        TagParser("CHAN") { changeDate = parseChangeDate(lineIterator) },
        noteParser(notes),
        sourceCitationParser(sourceCitations, lineIterator),
        multimediaLinkParser(multimediaLinks)
    )

    return FamilyGroup(
        id = FamilyGroupId(id),
        events = events,
        husbandId = husband,
        wifeId = wife,
        childrenIds = children,
        nbrOfChildren = nbrOfChildren,
        changeDate = changeDate,
        notes = notes,
        sourceCitations = sourceCitations,
        multimediaLinks = multimediaLinks
    )
}

private fun parseFamilyEvent(lineIterator: LineIterator): FamilyEvent {
    val tag = lineIterator.current().tag()
    val eventType = FamilyEventType.fromTagNameStrict(tag)

    var husbandAge: String? = null
    var wifeAge: String? = null
    val detail = EventDetailAccumulator()

    lineIterator.parseByTag(
        TagParser("HUSB") { husbandAge = parseFamilyEventPersonAge(lineIterator) },
        TagParser("WIFE") { wifeAge = parseFamilyEventPersonAge(lineIterator) },
        *detail.tagParsers(lineIterator)
    )

    return FamilyEvent(
        eventType,
        FamilyEventDetail(husbandAge, wifeAge, detail.build())
    )
}

private fun parseFamilyEventPersonAge(lineIterator: LineIterator): String? {
    var age: String? = null

    lineIterator.parseByTag(
        TagParser("AGE") { age = it }
    )

    return age
}

// The EVENT_DETAIL substructure (TYPE, DATE, PLAC, ADDR, NOTE, SOUR, OBJE) is shared
// by every event and attribute. This accumulator collects those fields as its tag
// parsers fire and builds the EvenDetail, so each caller only adds its own extra tags.
private class EventDetailAccumulator {
    var type: String? = null
    var date: DateValue? = null
    var place: Place? = null
    var address: Address? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    fun tagParsers(lineIterator: LineIterator) = arrayOf(
        TagParser("TYPE") { type = it },
        dateParser { date = it },
        TagParser("PLAC") { name -> place = parsePlace(name, lineIterator) },
        TagParser("ADDR") { address = parseAddress(lineIterator) },
        noteParser(notes),
        sourceCitationParser(sourceCitations, lineIterator),
        multimediaLinkParser(multimediaLinks)
    )

    fun build() = EvenDetail(type, date, place, address, notes, sourceCitations, multimediaLinks)
}

// INDIVIDUAL_EVENT_DETAIL wraps EVENT_DETAIL with an AGE at the time of the event.
private class IndividualEventDetailAccumulator {
    var age: String? = null
    private val detail = EventDetailAccumulator()

    fun tagParsers(lineIterator: LineIterator) = arrayOf(
        TagParser("AGE") { age = it },
        *detail.tagParsers(lineIterator)
    )

    fun build() = IndividualEventDetails(detail.build(), age)
}

private fun parsePlace(name: String, lineIterator: LineIterator): Place {
    var latitude: Double? = null
    var longitude: Double? = null
    val notes = mutableListOf<String>()

    lineIterator.parseByTag(
        TagParser("MAP") { parseCoordinates(lineIterator, { longitude = it }, { latitude = it }) },
        noteParser(notes)
    )

    return Place(
        name,
        longitude,
        latitude,
        notes.toList()
    )
}

private fun parseCoordinates(
    lineIterator: LineIterator,
    longitudeSetter: (Double) -> Unit,
    latitudeSetter: (Double) -> Unit
) {
    lineIterator.parseByTag(
        TagParser("LATI") { parseCoordinate(it).let(latitudeSetter) },
        TagParser("LONG") { parseCoordinate(it).let(longitudeSetter) }
    )
}

// GEDCOM stores coordinates with a hemisphere prefix (N/S for latitude, E/W for
// longitude). N/E are positive, S/W negative; the magnitude follows the letter.
private fun parseCoordinate(value: String): Double =
    when (value.firstOrNull()?.uppercaseChar()) {
        'N', 'E' -> value.drop(1).toDouble()
        'S', 'W' -> -value.drop(1).toDouble()
        else -> value.toDouble()
    }

private fun parseAddress(lineIterator: LineIterator): Address {
    var address1: String? = null
    var address2: String? = null
    var address3: String? = null
    var city: String? = null
    var state: String? = null
    var postalCode: String? = null
    var country: String? = null
    val phoneNumbers = mutableListOf<String>()
    val emails = mutableListOf<String>()
    val faxes = mutableListOf<String>()
    val wwws = mutableListOf<String>()

    lineIterator.parseByTag(
        TagParser("ADR1") { address1 = it },
        TagParser("ADR2") { address2 = it },
        TagParser("ADR3") { address3 = it },
        TagParser("CITY") { city = it },
        TagParser("STAE") { state = it },
        TagParser("POST") { postalCode = it },
        TagParser("CTRY") { country = it }
    )

    while (lineIterator.hasNext()) {
        val peekedLine = lineIterator.peek()
        when (peekedLine.tag()) {
            "PHON" -> phoneNumbers.add(peekedLine.content())
            "EMAIL" -> emails.add(peekedLine.content())
            "FAX" -> faxes.add(peekedLine.content())
            "WWW" -> wwws.add(peekedLine.content())
            else -> break
        }
        lineIterator.next()
    }

    return Address(
        address1,
        address2,
        address3,
        city,
        state,
        postalCode,
        country,
        phoneNumbers,
        emails,
        faxes,
        wwws
    )
}

private fun parseSourceCitation(id: String, lineIterator: LineIterator): SourceCitation {
    val source = SourceId(id)
    var page: String? = null
    var eventTypeCitedFrom: EventTypeCitedFrom? = null
    var data: SourceCitation.Data? = null
    val notes = mutableListOf<String>()
    val multimediaLinks = mutableListOf<MultimediaLink>()
    var qualityAssessment: QUAY? = null

    lineIterator.parseByTag(
        TagParser("PAGE") { page = it },
        TagParser("EVEN") { tagName -> eventTypeCitedFrom = parseEventTypeCitedFrom(tagName, lineIterator) },
        TagParser("DATA") { data = parseSourceCitationData(lineIterator) },
        noteParser(notes),
        multimediaLinkParser(multimediaLinks),
        TagParser("QUAY") { quayValue ->
            qualityAssessment = QUAY.fromValue(quayValue.toInt())
            if (qualityAssessment == null) {
                logger.warn("Unrecognized QUAY value '$quayValue' for source citation '$id'")
            }
        }
    )

    return SourceCitation(
        source,
        page,
        eventTypeCitedFrom,
        data,
        notes,
        multimediaLinks,
        qualityAssessment
    )
}

private fun parseSourceCitationData(lineIterator: LineIterator): SourceCitation.Data {
    var date: DateValue? = null
    var text: String? = null

    lineIterator.parseByTag(
        dateParser { date = it },
        TagParser("TEXT") { text = it }
    )

    return SourceCitation.Data(
        date,
        text
    )
}

// Ideally the type would be validated against EventType.fromTagName, but MyHeritage
// exports break this GEDCOM rule by using arbitrary tag names here, so it's kept as-is.
private fun parseEventTypeCitedFrom(tagName: String, lineIterator: LineIterator): EventTypeCitedFrom {
    var roleInEvent: String? = null

    lineIterator.parseByTag(
        TagParser("ROLE") { roleInEvent = it }
    )

    return EventTypeCitedFrom(
        tagName,
        roleInEvent
    )
}

private fun parseSource(id: String, lineIterator: LineIterator): Source {
    var data: Source.Data? = null
    var author: String? = null
    var title: String? = null
    var abbreviation: String? = null
    var publicationFacts: String? = null
    var text: String? = null
    var automatedRecordId: String? = null
    val notes = mutableListOf<String>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    lineIterator.parseByTag(
        TagParser("DATA") { data = parseSourceData(lineIterator) },
        TagParser("AUTH") { author = it },
        TagParser("TITL") { title = it },
        TagParser("ABBR") { abbreviation = it },
        TagParser("PUBL") { publicationFacts = it },
        TagParser("TEXT") { text = it },
//        REPO
//        REFN
        TagParser("RIN") { automatedRecordId = it },
//        CHAN
        noteParser(notes),
        multimediaLinkParser(multimediaLinks)
    )

    return Source(
        SourceId(id),
        data,
        author,
        title,
        abbreviation,
        publicationFacts,
        text,
        automatedRecordId,
        notes,
        multimediaLinks
    )
}

private fun parseSourceData(lineIterator: LineIterator): Source.Data {
    val events = mutableListOf<Source.Data.Event>()
    var responsibleAgency: String? = null
    val notes = mutableListOf<String>()

    lineIterator.parseByTag(
        TagParser("EVEN") { type -> parseSourceDataEvent(type, lineIterator).let(events::add) },
        TagParser("AGNC") { responsibleAgency = it },
        noteParser(notes)
    )

    return Source.Data(
        events,
        responsibleAgency,
        notes
    )
}

private fun parseSourceDataEvent(type: String, lineIterator: LineIterator): Source.Data.Event {
    var date: DateValue? = null
    var place: String? = null

    lineIterator.parseByTag(
        dateParser { date = it },
        TagParser("PLAC") { place = it }
    )

    return Source.Data.Event(
        type,
        date,
        place
    )
}

internal fun parseDateValue(dateString: String): DateValue =
    dateString
        .split(" ")
        .let { dateParts ->
            parseDate(dateParts)
                ?: parseDatePeriod(dateParts)
                ?: parseDateRange(dateParts)
                ?: parseDateApproximated(dateParts)
                ?: parseDatePhraseExt(dateParts)
                ?: DatePhrase(dateString)
        }

private fun parseDatePeriod(dateParts: List<String>): DatePeriod? {
    if (dateParts.firstOrNull() != "FROM") {
        return null
    }

    val toIndex = dateParts.indexOf("TO")
    if (toIndex <= 0) {
        return null
    }

    val from = parseDate(dateParts.subList(1, toIndex)) ?: return null
    val to = parseDate(dateParts.subList(toIndex + 1, dateParts.size)) ?: return null

    return DatePeriod(from, to)
}

private fun parseDateRange(dateParts: List<String>): DateRange? {
    return when (dateParts.firstOrNull()) {
        "BEF" -> parseDate(dateParts.drop(1))?.let(::DateRangeBefore)
        "AFT" -> parseDate(dateParts.drop(1))?.let(::DateRangeAfter)
        "BET" -> {
            val andIndex = dateParts.indexOf("AND")
            if (andIndex <= 0) {
                return null
            }

            val date1 = parseDate(dateParts.subList(1, andIndex)) ?: return null
            val date2 = parseDate(dateParts.subList(andIndex + 1, dateParts.size)) ?: return null

            DateRangeBetween(date1, date2)
        }

        else -> null
    }
}

private fun parseDateApproximated(dateParts: List<String>): DateApproximated? {
    if (dateParts.firstOrNull() !in setOf("ABT", "CAL", "EST")) {
        return null
    }

    return parseDate(dateParts.drop(1))?.let(::DateApproximated)
}

private fun parseDatePhraseExt(dateParts: List<String>): DatePhraseExt? {
    // TODO
    return null
}

private val yearRegex = """^(\d{3,4})(?: (BCE|BC|B\.C\.))?$""".toRegex()
private val monthYearRegex = """^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})$""".toRegex()
private val dayMonthYearRegex = """^(\d{1,2}) (JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})$""".toRegex()
private val dayMonthRegex = """^(\d{1,2}) (JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)$""".toRegex()
private val monthDualYearRegex = """^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})/(\d{3,4})$""".toRegex()
private val dayMonthDualYearRegex =
    """^(\d{1,2}) (JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})/(\d{3,4})$""".toRegex()

private fun parseDate(dateParts: List<String>): Date? {
    return dateParts
        .dropWhile { it == Calendars.GREGORIAN.id }
        .joinToString(" ")
        .parseByPattern(
            RegexParser(yearRegex) { (year, bc) ->
                Date(
                    Calendars.GREGORIAN,
                    GregorianCalendar(year = Year(year.toInt(), year.toInt()), beforeCommonEra = bc.isEmpty().not())
                )
            },
            RegexParser(monthYearRegex) { (month, year) ->
                Date(
                    Calendars.GREGORIAN,
                    GregorianCalendar(
                        month = GregorianCalendar.Month.valueOf(month),
                        year = Year(year.toInt(), year.toInt())
                    )
                )
            },
            RegexParser(dayMonthYearRegex) { (day, month, year) ->
                Date(
                    Calendars.GREGORIAN,
                    GregorianCalendar(
                        day = day.toInt(),
                        month = GregorianCalendar.Month.valueOf(month),
                        year = Year(year.toInt(), year.toInt())
                    )
                )
            },
            RegexParser(dayMonthRegex) { (day, month) ->
                Date(
                    Calendars.GREGORIAN,
                    GregorianCalendar(
                        day = day.toInt(),
                        month = GregorianCalendar.Month.valueOf(month)
                    )
                )
            },
            RegexParser(monthDualYearRegex) { (month, oldStyleYear, newStyleYear) ->
                Date(
                    Calendars.GREGORIAN,
                    GregorianCalendar(
                        month = GregorianCalendar.Month.valueOf(month),
                        year = Year(oldStyleYear.toInt(), newStyleYear.toInt())
                    )
                )
            },
            RegexParser(dayMonthDualYearRegex) { (day, month, oldStyleYear, newStyleYear) ->
                Date(
                    Calendars.GREGORIAN,
                    GregorianCalendar(
                        day = day.toInt(),
                        month = GregorianCalendar.Month.valueOf(month),
                        year = Year(oldStyleYear.toInt(), newStyleYear.toInt())
                    )
                )
            }
        )
}

private fun <T> String.parseByPattern(vararg parsers: RegexParser<T>): T? {
    for ((regex, parser) in parsers) {
        regex.matchEntire(this)
            ?.let {
                return parser(it.destructured)
            }
    }
    return null
}

data class RegexParser<T>(
    val regex: Regex,
    val parser: (result: MatchResult.Destructured) -> T
)

const val BYTE_ORDER_MARK = '﻿'

private fun getByteOrderMark(lines: List<String>): Char? = lines[0].firstOrNull()

internal fun stripByteOrderMark(lines: List<String>): List<String> {
    if (lines.isEmpty() || getByteOrderMark(lines) != BYTE_ORDER_MARK) {
        return lines
    }

    return listOf(lines[0].drop(1)) + lines.drop(1)
}

// parseByProperty's recursive descent always needs a "super line" one level shallower
// than whatever it's currently parsing, so the very first real line (depth 0) needs an
// artificial parent. "-1" is a line consisting of just a depth with no tag, one level
// above the shallowest depth any real GEDCOM record can have.
private fun insertDummyFirstLine(lines: List<String>) = listOf("-1") + lines

internal fun mergeOrphanedLines(lines: List<String>): List<String> {
    val merged = mutableListOf<String>()
    lines.forEach { line ->
        if (merged.isEmpty() || line.takeWhile(Char::notSpace).toIntOrNull() != null) {
            merged.add(line)
        } else {
            merged[merged.lastIndex] = merged.last() + line
        }
    }
    return merged
}

data class Line(val lineNbr: Int, val line: String) {
    fun depth() = line.takeWhile(Char::notSpace).toInt()
    fun tag() = line.dropWhile(Char::notSpace).drop(1).takeWhile(Char::notSpace)
    fun content() = line.dropWhile(Char::notSpace).drop(1).dropWhile(Char::notSpace).drop(1)

    fun inProperFormat(): Boolean {
        try {
            depth()
        } catch (_: NumberFormatException) {
            return false
        }
        return true
    }

    override fun toString(): String {
        return "$lineNbr: $line"
    }
}

private fun List<Line>.lineIterator() = LineIterator(this)
class LineIterator(lines: List<Line>) : PeekableIterator<Line>(lines) {
    fun parseByContent(vararg contentParsers: ContentParser, knownUnhandled: Set<String> = emptySet()) {
        parseByProperty(
            Line::content,
            Line::tag,
            contentParsers,
            ContentParser::content,
            ContentParser::parser,
            knownUnhandled
        )
    }

    fun parseByTag(vararg tagParsers: TagParser) {
        parseByProperty(
            Line::tag,
            Line::content,
            tagParsers,
            TagParser::tag,
            TagParser::parser
        )
    }

    private fun <T> parseByProperty(
        property: (Line) -> String,
        value: (Line) -> String,
        propertyParsers: Array<T>,
        propertyParserKey: (T) -> String,
        propertyParser: (T) -> (String) -> Unit,
        knownUnhandled: Set<String> = emptySet()
    ) {
        // Index the parsers by key once so each sub-line is an O(1) lookup rather than a
        // linear scan; INDI alone registers ~30 parsers. putIfAbsent keeps the first
        // registered parser on a key collision, matching the previous firstOrNull scan.
        val parsersByKey = LinkedHashMap<String, T>(propertyParsers.size)
        propertyParsers.forEach { parser -> parsersByKey.putIfAbsent(propertyParserKey(parser), parser) }

        val superLine = current()
        val currentDepth = superLine.depth()
        while (hasNext() && peek().depth() > currentDepth) {
            val subLine = next()
            if (subLine.inProperFormat().not()) {
                logger.error("Could not parse line ${subLine.lineNbr}: ${subLine.line}")
                continue
            }
            val matchedParser = parsersByKey[property(subLine)]
            if (matchedParser != null) {
                propertyParser(matchedParser)(value(subLine))
            } else {
                if (property(subLine) in knownUnhandled) {
                    logger.debug("Skipping unhandled record at line ${subLine.lineNbr}: ${subLine.line}")
                } else {
                    logger.warn("No parser found for property ${property(subLine)}, skipping line ${subLine.lineNbr}: ${subLine.line}")
                }
                skipSubtree(subLine.depth())
            }
        }
    }

    private fun skipSubtree(parentDepth: Int) {
        while (hasNext() && peek().depth() > parentDepth) {
            next()
        }
    }
}

private fun noteParser(notes: MutableList<String>) = TagParser("NOTE") { notes.add(it) }
private fun multimediaLinkParser(multimediaLinks: MutableList<MultimediaLink>) =
    TagParser("OBJE") { link -> multimediaLinks.add(MultimediaLink(link)) }

private fun sourceCitationParser(sourceCitations: MutableList<SourceCitation>, lineIterator: LineIterator) =
    TagParser("SOUR") { id -> sourceCitations.add(parseSourceCitation(id, lineIterator)) }

private fun dateParser(assignDate: (DateValue) -> Unit) =
    TagParser("DATE") { dateString -> assignDate(parseDateValue(dateString)) }

data class TagParser(
    val tag: String,
    val parser: (String) -> Unit
)

data class ContentParser(
    val content: String,
    val parser: (String) -> Unit
)

data class ParseContainer(
    val familyGroups: MutableCollection<FamilyGroup> = mutableListOf(),
    val individuals: MutableCollection<Individual> = mutableListOf(),
    val sources: MutableList<Source> = mutableListOf()
)

private fun String?.toConfirmed() = when (this) {
    "Y" -> true
    "" -> false
    else -> throw IllegalArgumentException("'$this' is not a valid confirmed string")
}

// The Y/NULL "confirmed" slot is occasionally filled with an arbitrary string by
// exporters like MyHeritage. Rather than aborting the whole parse, log it and treat
// the event as unconfirmed so the rest of its detail is still captured.
private fun parseConfirmed(value: String?, context: String): Boolean =
    try {
        value.toConfirmed()
    } catch (e: IllegalArgumentException) {
        logger.warn("Unrecognized confirmed value '$value' for $context, treating as unconfirmed")
        false
    }

private fun Char.notSpace() = this != ' '