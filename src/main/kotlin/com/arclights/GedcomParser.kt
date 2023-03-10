package com.arclights

import org.slf4j.LoggerFactory
import java.time.LocalDate

private val logger = LoggerFactory.getLogger("GedcomParser")

fun parseGedcom(lines: List<String>): Gedcom {
    val lineIterator = lines
        .let(::stripByteOrderMark)
        .let(::insertDummyFirstLine)
        .mapIndexed(::Line)
        .also(::validateFormat)
        .mergeConcAndCont()
        .lineIterator()

    val parseContainer = ParseContainer()

    println("parsing\n")
    lineIterator.parseByContent(
        ContentParser("FAM") { id ->
            parseFamilyGroup(id, lineIterator).let { parseContainer.familyGroups.add(it) }
        },
        ContentParser("INDI") { id ->
            parseIndividual(id, lineIterator).let { parseContainer.individuals.add(it) }
        },
        ContentParser("SOUR") { id ->
            parseSource(id, lineIterator).let { parseContainer.sources.add(it) }
        }
    )

    println("\nparsed")
//    println(parseContainer)
    println("Nbr of family groups: ${parseContainer.familyGroups.size}")
    println("Nbr of individuals: ${parseContainer.individuals.size}")
    println("Nbr of sources: ${parseContainer.sources.size}")
    return Gedcom(
        familyGroups = parseContainer.familyGroups.associateBy { it.id },
        individuals = parseContainer.individuals.associateBy { it.id },
        sources = parseContainer.sources.associateBy { it.id }
    )
}

fun validateFormat(lines: List<Line>) {
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

fun List<Line>.mergeConcAndCont(): List<Line> {
    val concOrCont = setOf("CONC", "CONT")
    return this.fold(listOf()) { lines, line ->
        if (line.tag() in concOrCont) {
            val lastLine = lines.last()
            val updatedLastLine = lastLine.copy(line = lastLine.line + line.content())
            lines.dropLast(1).plus(updatedLastLine)
        } else {
            lines.plus(line)
        }
    }
}

fun parseIndividual(id: String, lineIterator: LineIterator): Individual {
    println("Parsing individual")

    val names = mutableListOf<IndividualName>()
    var sex: Sex? = null
    val events = mutableListOf<IndividualEvent>()
    val attributes = mutableListOf<IndividualAttribute>()
    val childToFamilies = mutableListOf<ChildToFamilyLink>()
    val spouseToFamilies = mutableListOf<SpouseToFamilyLink>()
    val associations = mutableListOf<Association>()
    val changeDate: LocalDate? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    lineIterator.parseByTag(
        TagParser("NAME") { name -> parsePersonalName(name, lineIterator).let(names::add) },
        TagParser("SEX") { sexValue -> sex = Sex.fromValue(sexValue) },
        noteParser(notes),
        multimediaLinkParser(multimediaLinks),
        sourceCitationParser(sourceCitations, lineIterator),
        TagParser("BIRT") { events.add(parseBirthEvent(lineIterator)) },
        TagParser("DEAT") { events.add(parseDeathEvent(it.toConfirmed(), lineIterator)) },
        TagParser("CHR") {
            try {
                it.toConfirmed()
            } catch (e: IllegalArgumentException) {
                // For some reason, MyHeritage sometimes sets a string in the Y/NULL place
                logger.error("Invalid line '${lineIterator.current()}', cannot parse christening event", e)
                null
            }?.let { confirmed -> events.add(parseChristeningEvent(confirmed, lineIterator)) }
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
//                "CAST",
//                "DSCR",
//                "EDUC",
//                "IDNO",
//                "NATI",
//                "NCHI",
//                "NMR",
//                "OCCU",
//                "PROP",
//                "RELI",
//                "RESI",
//                "TITL",
//                "FACT" -> parseattributes
        TagParser("FAMC") { familyId ->
            parseChildToFamilyLink(
                familyId,
                lineIterator
            ).let(childToFamilies::add)
        },
        TagParser("FAMS") { familyId -> parseSpouseToFamilyLink(familyId, lineIterator).let(spouseToFamilies::add) }
//                "ASSO" -> parseAssociation
//                "CHAN" -> parseChangeDate
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
    ).also { println(it) }
        .also { println(it.events) }
}

fun parsePersonalName(name: String, lineIterator: LineIterator): IndividualName {
    println("Parsing personal name")
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

fun parseChildToFamilyLink(familyId: String, lineIterator: LineIterator): ChildToFamilyLink {
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

fun parseBirthEvent(lineIterator: LineIterator): BirthEvent {
    var familyId: FamilyGroupId? = null
    var age: String? = null
    var eventOrFactClassification: String? = null
    var date: DateValue? = null
    var place: Place? = null
    var address: Address? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    lineIterator.parseByTag(
        TagParser("FAMC") { familyId = FamilyGroupId(it) },
        *getIndividualEventDetailTagParsers(
            { age = it },
            { eventOrFactClassification = it },
            { date = it },
            { place = it },
            { address = it },
            notes,
            sourceCitations,
            multimediaLinks,
            lineIterator
        )
    )

    return BirthEvent(
        IndividualEventDetails(
            EvenDetail(
                eventOrFactClassification,
                date,
                place,
                address,
                notes,
                sourceCitations,
                multimediaLinks
            ),
            age
        ),
        familyId
    )
}

fun parseDeathEvent(confirmed: Boolean, lineIterator: LineIterator): DeathEvent {
    var age: String? = null
    var eventOrFactClassification: String? = null
    var date: DateValue? = null
    var place: Place? = null
    var address: Address? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    lineIterator.parseByTag(
        *getIndividualEventDetailTagParsers(
            { age = it },
            { eventOrFactClassification = it },
            { date = it },
            { place = it },
            { address = it },
            notes,
            sourceCitations,
            multimediaLinks,
            lineIterator
        )
    )

    return DeathEvent(
        confirmed,
        IndividualEventDetails(
            EvenDetail(
                eventOrFactClassification,
                date,
                place,
                address,
                notes,
                sourceCitations,
                multimediaLinks
            ),
            age
        )
    )
}

fun parseChristeningEvent(confirmed: Boolean, lineIterator: LineIterator): ChristeningEvent {
    var familyId: FamilyGroupId? = null
    var age: String? = null
    var eventOrFactClassification: String? = null
    var date: DateValue? = null
    var place: Place? = null
    var address: Address? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    lineIterator.parseByTag(
        TagParser("FAMC") { familyId = FamilyGroupId(it) },
        *getIndividualEventDetailTagParsers(
            { age = it },
            { eventOrFactClassification = it },
            { date = it },
            { place = it },
            { address = it },
            notes,
            sourceCitations,
            multimediaLinks,
            lineIterator
        )
    )

    return ChristeningEvent(
        confirmed,
        IndividualEventDetails(
            EvenDetail(
                eventOrFactClassification,
                date,
                place,
                address,
                notes,
                sourceCitations,
                multimediaLinks
            ),
            age
        ),
        familyId
    )
}

fun getIndividualEventDetailTagParsers(
    assignAge: (String) -> Unit,
    assignEventOrFactClassification: (String) -> Unit,
    assignDate: (DateValue) -> Unit,
    assignPlace: (Place) -> Unit,
    assignAddress: (Address) -> Unit,
    notes: MutableList<String>,
    sourceCitations: MutableList<SourceCitation>,
    multimediaLinks: MutableList<MultimediaLink>,
    lineIterator: LineIterator
) = arrayOf(
    TagParser("AGE", assignAge),
    * getEventDetailTagParsers(
        assignEventOrFactClassification,
        assignDate,
        assignPlace,
        assignAddress,
        notes,
        sourceCitations,
        multimediaLinks,
        lineIterator
    )
)

fun parseFamilyGroup(id: String, lineIterator: LineIterator): FamilyGroup {
    println("Parsing family group")

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
    ).also { println(it) }
}

fun parseFamilyEvent(lineIterator: LineIterator): FamilyEvent {
    val tag = lineIterator.current().tag()
    val eventType = FamilyEventType.fromTagNameStrict(tag)

    var husbandAge: Int? = null
    var wifeAge: Int? = null
    var eventOrFactClassification: String? = null
    var date: DateValue? = null
    var place: Place? = null
    var address: Address? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    lineIterator.parseByTag(
        TagParser("HUSB") { husbandAge = it.toInt() },
        TagParser("WIFE") { wifeAge = it.toInt() },
        *getEventDetailTagParsers(
            { eventOrFactClassification = it },
            { date = it },
            { place = it },
            { address = it },
            notes,
            sourceCitations,
            multimediaLinks,
            lineIterator
        )
    )

    return FamilyEvent(
        eventType,
        FamilyEventDetail(
            husbandAge,
            wifeAge,
            EvenDetail(
                eventOrFactClassification,
                date,
                place,
                address,
                notes,
                sourceCitations,
                multimediaLinks
            )
        )
    )
}

fun getEventDetailTagParsers(
    assignEventOrFactClassification: (String) -> Unit,
    assignDate: (DateValue) -> Unit,
    assignPlace: (Place) -> Unit,
    assignAddress: (Address) -> Unit,
    notes: MutableList<String>,
    sourceCitations: MutableList<SourceCitation>,
    multimediaLinks: MutableList<MultimediaLink>,
    lineIterator: LineIterator
) = arrayOf(
    TagParser("TYPE", assignEventOrFactClassification),
    dateParser(assignDate),
    TagParser("PLAC") { name -> parserPlace(name, lineIterator).let(assignPlace) },
    TagParser("ADDR") { parseAddress(lineIterator).let(assignAddress) },
    noteParser(notes),
    sourceCitationParser(sourceCitations, lineIterator),
    multimediaLinkParser(multimediaLinks)
)

fun parserPlace(name: String, lineIterator: LineIterator): Place {
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

fun parseCoordinates(
    lineIterator: LineIterator,
    longitudeSetter: (Double) -> Unit,
    latitudeSetter: (Double) -> Unit
) {
    lineIterator.parseByTag(
        TagParser("LATI") { it.drop(1).toDouble().let(latitudeSetter) },
        TagParser("LONG") { it.drop(1).toDouble().let(longitudeSetter) }
    )
}

fun parseAddress(lineIterator: LineIterator): Address {
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

fun parseSourceCitation(id: String, lineIterator: LineIterator): SourceCitation {
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
        TagParser("QUAY") { qualityAssessment = QUAY.fromValue(it.toInt()) }
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

fun parseSourceCitationData(lineIterator: LineIterator): SourceCitation.Data {
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

fun parseEventTypeCitedFrom(tagName: String, lineIterator: LineIterator): EventTypeCitedFrom? {
    val type = tagName
    // MyHeritage is breaking this GEDCOM rule
//    return EventType.fromTagName(tagName)
//        ?.let { type ->
    var roleInEvent: String? = null

    lineIterator.parseByTag(
        TagParser("ROLE") { roleInEvent = it }
    )

    return EventTypeCitedFrom(
        type,
        roleInEvent
    )
//        }
//        ?: run {
//            println("Could not parse event type cited from with name $tagName")
//            return null
//        }

}

fun parseSource(id: String, lineIterator: LineIterator): Source {
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
    ).also { println(it) }
}

fun parseSourceData(lineIterator: LineIterator): Source.Data {
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

fun parseSourceDataEvent(type: String, lineIterator: LineIterator): Source.Data.Event {
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

fun parseDateValue(dateString: String): DateValue =
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

fun parseDatePeriod(dateParts: List<String>): DatePeriod? {
    // TODO
    return null
}

fun parseDateRange(dateParts: List<String>): DateRange? {
    // TODO
    return null
}

fun parseDateApproximated(dateParts: List<String>): DateApproximated? {
    // TODO
    return null
}

fun parseDatePhraseExt(dateParts: List<String>): DatePhraseExt? {
    // TODO
    return null
}

fun parseDate(dateParts: List<String>): Date? {
    val yearRegex = """^(\d{3,4})(?: (BCE|BC|B\.C\.))?$""".toRegex()
    val monthYearRegex = """^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})$""".toRegex()
    val dayMonthYearRegex = """^(\d{1,2}) (JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})$""".toRegex()
    val dayMonthRegex = """^(\d{1,2}) (JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)$""".toRegex()
    val monthDualYearRegex = """^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})/(\d{3,4})$""".toRegex()
    val dayMonthDualYearRegex =
        """^(\d{1,2}) (JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})/(\d{3,4})$""".toRegex()
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

fun <T> String.parseByPattern(vararg parsers: RegexParser<T>): T? {
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

fun getByteOrderMark(lines: List<String>): Char = lines[0][0]

fun stripByteOrderMark(lines: List<String>) = lines[0]
    .drop(1)
    .let { listOf(it) }
    .let { it + lines.drop(1) }

fun insertDummyFirstLine(lines: List<String>) = listOf("-1") + lines

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

fun List<Line>.lineIterator() = LineIterator(this)
class LineIterator(lines: List<Line>) : PeekableIterator<Line>(lines) {
    fun parseByContent(vararg contentParsers: ContentParser) {
        parseByProperty(
            Line::content,
            Line::tag,
            contentParsers,
            ContentParser::content,
            ContentParser::parser
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
        propertyParser: (T) -> (String) -> Unit
    ) {
        val superLine = current()
        val currentDepth = superLine.depth()
        while (hasNext() && peek().depth() > currentDepth) {
            val subLine = next()
            if (subLine.inProperFormat().not()) {
                logger.error("Could not parse line ${subLine.lineNbr}: ${subLine.line}")
                continue
            }
            propertyParsers.firstOrNull { parser -> propertyParserKey(parser) == property(subLine) }
//                ?.also { println("Found parser for property ${property(subLine)}") }
                ?.let(propertyParser)
                ?.let { parse -> parse(value(subLine)) }
                ?: logger.warn("No parser found for property ${property(subLine)}, skipping line ${subLine.lineNbr}: ${subLine.line}")
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

fun String?.toConfirmed() = when (this) {
    "Y" -> true
    "" -> false
    else -> throw IllegalArgumentException("'$this' is not a valid confirmed string")
}

fun Char.notSpace() = this != ' '