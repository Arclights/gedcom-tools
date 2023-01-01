package com.arclights

import org.slf4j.LoggerFactory
import java.time.LocalDate

private val logger = LoggerFactory.getLogger("GedcomParser")

fun parseGedcom(lines: List<String>): Gedcom {
    val lineIterator = lines
        .let(::stripByteOrderMark)
        .map(::Line)
        .peekableIterator()

    val parseContainer = ParseContainer()

    println("${"0".toInt()}")
    println("parsing\n")
    while (lineIterator.hasNext()) {
        val line = lineIterator.peek()

        if (line.inProperFormat().not()) {
            logger.error("Could not parse line: ${line.line}")
            lineIterator.next()
            continue
        }

//        println(line.line)
        parseTopLevelEntities(lineIterator, parseContainer)
//        lineIterator.next()
//        println("${line.depth()} ${line.tag()} ${line.content()}")
    }

    println("\nparsed")
//    println(parseContainer)
    println("Nbr of family groups: ${parseContainer.familyGroups.size}")
    return Gedcom(
        familyGroups = parseContainer.familyGroups.toList()
    )
}

fun parseTopLevelEntities(lineIterator: PeekableIterator<Line>, parseContainer: ParseContainer) {
    when (lineIterator.peek().content()) {
        "FAM" -> parseFamilyGroup(lineIterator).also { parseContainer.familyGroups.add(it) }
        else -> {
            println("Skipping: ${lineIterator.peek().line}")
            lineIterator.next()
        }
    }
}

fun parseFamilyGroup(lineIterator: PeekableIterator<Line>): FamilyGroup {
    println("Parsing family group")
    val (_, depth, id) = lineIterator.next()

    val events = mutableListOf<FamilyEvent>()
    var husband: IndividualId? = null
    var wife: IndividualId? = null
    val children = mutableListOf<IndividualId>()
    var nbrOfChildren: Int? = null
    var changeDate: LocalDate? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    while (lineIterator.hasNext() && lineIterator.peek().depth() > depth) {
        with(lineIterator.next()) {
            when (tag()) {
                "HUSB" -> husband = IndividualId(content())
                "WIFE" -> wife = IndividualId(content())
                "CHIL" -> children.add(IndividualId(content()))
                "NCHI" -> nbrOfChildren = content().toInt()
                "ANUL",
                "CENS",
                "DIV",
                "DIVF",
                "ENGA",
                "MARB",
                "MARC",
                "MARR",
                "MARL",
                "MARS" -> parseFamilyEvent(this, lineIterator).let { events.add(it) }

                "NOTE" -> notes.add(content())
                "SOUR" -> parseSourceCitationData(this, lineIterator)
                "OBJE" -> multimediaLinks.add(MultimediaLink(content()))

                else -> println("Skipping: $line")
            }
        }
    }

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

fun parseFamilyEvent(line: Line, lineIterator: PeekableIterator<Line>): FamilyEvent {
    val (_, depth, tag) = line
    val eventType = FamilyEventType.fromTagNameStrict(tag)

    var husbandAge: Int? = null
    var wifeAge: Int? = null
    var eventDetail: EvenDetail? = null
    var eventOrFactClassification: String? = null
    var date: LocalDate? = null
    var place: Place? = null
    var address: Address? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    while (lineIterator.hasNext() && lineIterator.peek().depth() > depth) {
        with(lineIterator.next()) {
            when (tag()) {
                "HUSB" -> lineIterator.next().content().toInt().let { husbandAge = it }
                "WIFE" -> lineIterator.next().content().toInt().let { wifeAge = it }
                else -> parseEventDetailPart(
                    this,
                    lineIterator,
                    { eventOrFactClassification = it },
                    { date = it },
                    { place = it },
                    { address = it },
                    { notes.add(it) },
                    { sourceCitations.add(it) },
                    { multimediaLinks.add(it) }
                )
            }
        }
    }

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

fun parseEventDetailPart(
    line: Line,
    lineIterator: PeekableIterator<Line>,
    eventOrFactClassificationSetter: (String) -> Unit,
    dateSetter: (LocalDate) -> Unit,
    placeSetter: (Place) -> Unit,
    addressSetter: (Address) -> Unit,
    notesSetter: (String) -> Unit,
    sourceCitationsSetter: (SourceCitation) -> Unit,
    multimediaLinksSetter: (MultimediaLink) -> Unit
) {
    when (line.tag()) {
        "TYPE" -> line.content().let(eventOrFactClassificationSetter)
//            "DATE"->
        "PLAC" -> parserPlace(line, lineIterator).let(placeSetter)
        "ADDR" -> parseAddress(line, lineIterator).let(addressSetter)
        "NOTE" -> line.content().let(notesSetter)
        "SOUR" -> parseSourceCitation(line, lineIterator).let(sourceCitationsSetter)
        "OBJE" -> multimediaLinksSetter(MultimediaLink(line.content()))
    }
}

fun parserPlace(line: Line, lineIterator: PeekableIterator<Line>): Place {
    val name = line.content()
    var latitude: Double? = null
    var longitude: Double? = null
    val notes = mutableListOf<String>()

    while (lineIterator.hasNext() && lineIterator.peek().depth() > line.depth()) {
        with(lineIterator.next()) {
            when (tag()) {
                "MAP" -> parseCoordinates(depth(), lineIterator, { longitude = it }, { latitude = it })
                "NOTE" -> notes.add(content())
                else -> println("Skipping: ${this.line}")
            }
        }
    }

    return Place(
        name,
        longitude,
        latitude,
        notes.toList()
    )
}

fun parseCoordinates(
    depth: Int,
    lineIterator: PeekableIterator<Line>,
    longitudeSetter: (Double) -> Unit,
    latitudeSetter: (Double) -> Unit
) {
    while (lineIterator.hasNext() && lineIterator.peek().depth() > depth) {
        with(lineIterator.next()) {
            when (tag()) {
                "LATI" -> content().drop(1).toDouble().let(latitudeSetter)
                "LONG" -> content().drop(1).toDouble().let(longitudeSetter)
                else -> println("Skipping: ${this.line}")
            }
        }
    }
}

fun parseAddress(line: Line, lineIterator: PeekableIterator<Line>): Address {
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

    while (lineIterator.hasNext() && lineIterator.peek().depth() > line.depth()) {
        with(lineIterator.next()) {
            when (tag()) {
                "ADR1" -> address1 = content()
                "ADR2" -> address2 = content()
                "ADR3" -> address3 = content()
                "CITY" -> city = content()
                "STAE" -> state = content()
                "POST" -> postalCode = content()
                "CTRY" -> country = content()
                else -> println("Skipping: ${this.line}")
            }
        }
    }

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

fun parseSourceCitation(line: Line, lineIterator: PeekableIterator<Line>): SourceCitation {
    val source = SourceId(line.content())
    var page: String? = null
    var eventTypeCitedFrom: EventTypeCitedFrom? = null
    var data: SourceCitation.Data? = null
    val notes = mutableListOf<String>()
    val multimediaLinks = mutableListOf<MultimediaLink>()
    var qualityAssessment: QUAY? = null

    while (lineIterator.hasNext() && lineIterator.peek().depth() > line.depth()) {
        with(lineIterator.next()) {
            when (tag()) {
                "PAGE" -> content().let { page = it }
                "EVEN" -> parseEventTypeCitedFrom(this, lineIterator).let { eventTypeCitedFrom = it }
                "DATA" -> parseSourceCitationData(this, lineIterator).let { data = it }
                "NOTE" -> notes.add(content())
                "OBJE" -> multimediaLinks.add(MultimediaLink(content()))
                "QUAY" -> QUAY.fromValue(content().toInt())?.let { qualityAssessment = it }
                else -> println("Skipping: ${this.line}")
            }
        }
    }

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

fun parseSourceCitationData(line: Line, lineIterator: PeekableIterator<Line>): SourceCitation.Data {
    var date: LocalDate? = null
    var text: String? = null

    while (lineIterator.hasNext() && lineIterator.peek().depth() > line.depth()) {
        with(lineIterator.next()) {
            when (tag()) {
                //"DATE"->
                "TEXT" -> text = content()
                else -> println("Skipping: ${this.line}")
            }
        }
    }

    return SourceCitation.Data(
        date,
        text
    )
}

fun parseEventTypeCitedFrom(line: Line, lineIterator: PeekableIterator<Line>): EventTypeCitedFrom {
    val type = EventType.fromTagNameStrict(line.content())
    var roleInEvent: String? = null

    while (lineIterator.hasNext() && lineIterator.peek().depth() > line.depth()) {
        with(lineIterator.next()) {
            when (tag()) {
                "ROLE" -> roleInEvent = content()
                else -> println("Skipping: ${this.line}")
            }
        }
    }

    return EventTypeCitedFrom(
        type,
        roleInEvent
    )
}

fun getByteOrderMark(lines: List<String>): Char = lines[0][0]

fun stripByteOrderMark(lines: List<String>) = lines[0]
    .drop(1)
    .let { listOf(it) }
    .let { it + lines.drop(1) }

data class Line(val line: String) {
    fun depth() = line.takeWhile(Char::notSpace).toInt()
    fun tag() = line.dropWhile(Char::notSpace).drop(1).takeWhile(Char::notSpace)
    fun content() = line.dropWhile(Char::notSpace).drop(1).dropWhile(Char::notSpace).drop(1)

    operator fun component2() = depth()
    operator fun component3() = tag()


    fun inProperFormat(): Boolean {
        try {
            depth()
        } catch (_: NumberFormatException) {
            return false
        }
        return true
    }
}

data class ParseContainer(
    val familyGroups: MutableCollection<FamilyGroup> = mutableListOf()
)

fun Char.notSpace() = this != ' '