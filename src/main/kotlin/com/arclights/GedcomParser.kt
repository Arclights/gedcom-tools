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
        // A level-0 line with no xref (e.g. HEAD, TRLR) has empty record content. HEAD is the
        // only such record we model; TRLR and anything else are drained without a warning.
        ContentParser("") { tag ->
            when (tag) {
                "HEAD" -> parseContainer.header = parseHeader(lineIterator)
                else -> lineIterator.skipChildren()
            }
        },
        ContentParser("FAM") { id ->
            parseFamilyGroup(id, lineIterator).let { parseContainer.familyGroups.add(it) }
        },
        ContentParser("INDI") { id ->
            parseIndividual(id, lineIterator).let { parseContainer.individuals.add(it) }
        },
        ContentParser("SOUR") { id ->
            parseSource(id, lineIterator).let { parseContainer.sources.add(it) }
        },
        ContentParser("REPO") { id ->
            parseRepository(id, lineIterator).let { parseContainer.repositories.add(it) }
        },
        ContentParser("NOTE") { id ->
            parseNoteRecord(id, lineIterator).let { parseContainer.noteRecords.add(it) }
        },
        ContentParser("OBJE") { id ->
            parseMultimediaRecord(id, lineIterator).let { parseContainer.multimediaRecords.add(it) }
        },
        ContentParser("SUBM") { id ->
            parseSubmitter(id, lineIterator).let { parseContainer.submitters.add(it) }
        },
        ContentParser("SUBN") { id ->
            parseSubmission(id, lineIterator).let { parseContainer.submissions.add(it) }
        }
    )

    logger.debug("parsed")
    logger.debug("Nbr of family groups: ${parseContainer.familyGroups.size}")
    logger.debug("Nbr of individuals: ${parseContainer.individuals.size}")
    logger.debug("Nbr of sources: ${parseContainer.sources.size}")
    return Gedcom(
        header = parseContainer.header,
        familyGroups = parseContainer.familyGroups.associateByLoggingDuplicates(FamilyGroup::id),
        individuals = parseContainer.individuals.associateByLoggingDuplicates(Individual::id),
        sources = parseContainer.sources.associateByLoggingDuplicates(Source::id),
        repositories = parseContainer.repositories.associateByLoggingDuplicates(Repository::id),
        noteRecords = parseContainer.noteRecords.associateByLoggingDuplicates(NoteRecord::id),
        multimediaRecords = parseContainer.multimediaRecords.associateByLoggingDuplicates(MultimediaRecord::id),
        submitters = parseContainer.submitters.associateByLoggingDuplicates(Submitter::id),
        submissions = parseContainer.submissions.associateByLoggingDuplicates(Submission::id)
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
    val ldsOrdinances = mutableListOf<LdsOrdinance>()
    val childToFamilies = mutableListOf<ChildToFamilyLink>()
    val spouseToFamilies = mutableListOf<SpouseToFamilyLink>()
    val associations = mutableListOf<Association>()
    var restriction: String? = null
    val aliases = mutableListOf<IndividualId>()
    val ancestorInterestSubmitterIds = mutableListOf<String>()
    val descendantInterestSubmitterIds = mutableListOf<String>()
    val submitterIds = mutableListOf<String>()
    var permanentRecordFileNumber: String? = null
    var ancestralFileNumber: String? = null
    val references = mutableListOf<String>()
    var automatedRecordId: String? = null
    var changeDate: LocalDate? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    val attributeParser: (String) -> Unit = { value ->
        parseIndividualAttribute(lineIterator.current().tag(), value, lineIterator).let(attributes::add)
    }
    // Every non-BIRT/CHR/DEAT individual event shares the INDIVIDUAL_EVENT_DETAIL grammar, so
    // they all route through one general parser keyed on their originating tag.
    val eventParser: (String) -> Unit = {
        events.add(parseGeneralIndividualEvent(lineIterator.current().tag(), lineIterator))
    }
    val ldsParser: (String) -> Unit = {
        ldsOrdinances.add(parseLdsOrdinance(lineIterator.current().tag(), lineIterator))
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
        multimediaLinkParser(multimediaLinks, lineIterator),
        sourceCitationParser(sourceCitations, lineIterator),
        TagParser("BIRT") { events.add(parseBirthEvent(lineIterator)) },
        TagParser("DEAT") { events.add(parseDeathEvent(parseConfirmed(it, "death event of '$id'"), lineIterator)) },
        TagParser("CHR") {
            events.add(parseChristeningEvent(parseConfirmed(it, "christening event of '$id'"), lineIterator))
        },
        TagParser("BURI", eventParser),
        TagParser("CREM", eventParser),
        TagParser("ADOP", eventParser),
        TagParser("BAPM", eventParser),
        TagParser("BARM", eventParser),
        TagParser("BASM", eventParser),
        TagParser("BLES", eventParser),
        TagParser("CHRA", eventParser),
        TagParser("CONF", eventParser),
        TagParser("FCOM", eventParser),
        TagParser("ORDN", eventParser),
        TagParser("NATU", eventParser),
        TagParser("EMIG", eventParser),
        TagParser("IMMI", eventParser),
        TagParser("CENS", eventParser),
        TagParser("PROB", eventParser),
        TagParser("WILL", eventParser),
        TagParser("GRAD", eventParser),
        TagParser("RETI", eventParser),
        TagParser("EVEN", eventParser),
        TagParser("BAPL", ldsParser),
        TagParser("CONL", ldsParser),
        TagParser("ENDL", ldsParser),
        TagParser("SLGC", ldsParser),
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
        TagParser("RESN") { restriction = it },
        TagParser("ALIA") { aliases.add(IndividualId(it)) },
        TagParser("ANCI") { ancestorInterestSubmitterIds.add(it) },
        TagParser("DESI") { descendantInterestSubmitterIds.add(it) },
        TagParser("SUBM") { submitterIds.add(it) },
        TagParser("RFN") { permanentRecordFileNumber = it },
        TagParser("AFN") { ancestralFileNumber = it },
        TagParser("REFN") { references.add(it) },
        TagParser("RIN") { automatedRecordId = it },
        TagParser("CHAN") { changeDate = parseChangeDate(lineIterator) }
    )

    return Individual(
        id = IndividualId(id),
        names = names,
        sex = sex,
        events = events,
        attributes = attributes,
        ldsOrdinances = ldsOrdinances,
        childToFamilies = childToFamilies,
        spouseToFamilies = spouseToFamilies,
        associations = associations,
        restriction = restriction,
        aliases = aliases,
        ancestorInterestSubmitterIds = ancestorInterestSubmitterIds,
        descendantInterestSubmitterIds = descendantInterestSubmitterIds,
        submitterIds = submitterIds,
        permanentRecordFileNumber = permanentRecordFileNumber,
        ancestralFileNumber = ancestralFileNumber,
        references = references,
        automatedRecordId = automatedRecordId,
        changeDate = changeDate,
        notes = notes,
        sourceCitations = sourceCitations,
        multimediaLinks = multimediaLinks
    )
}

private fun parseIndividualAttribute(type: String, value: String, lineIterator: LineIterator): GeneralIndividualAttribute {
    val detail = EventDetailAccumulator()

    lineIterator.parseByTag(*detail.tagParsers(lineIterator))

    return GeneralIndividualAttribute(type, value, detail.build())
}

private fun parseGeneralIndividualEvent(type: String, lineIterator: LineIterator): GeneralIndividualEvent {
    var familyId: FamilyGroupId? = null
    val detail = IndividualEventDetailAccumulator()

    lineIterator.parseByTag(
        // ADOP/BIRT/CHR carry a FAMC pointer to the family the event ties the individual to.
        TagParser("FAMC") { familyId = FamilyGroupId(it) },
        *detail.tagParsers(lineIterator)
    )

    return GeneralIndividualEvent(type, detail.build(), familyId)
}

private fun parseLdsOrdinance(type: String, lineIterator: LineIterator): LdsOrdinance {
    var date: DateValue? = null
    var templeCode: String? = null
    var place: Place? = null
    var status: String? = null
    var familyId: FamilyGroupId? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()

    lineIterator.parseByTag(
        dateParser { date = it },
        TagParser("TEMP") { templeCode = it },
        TagParser("PLAC") { name -> place = parsePlace(name, lineIterator) },
        TagParser("STAT") { status = it },
        TagParser("FAMC") { familyId = FamilyGroupId(it) },
        noteParser(notes),
        sourceCitationParser(sourceCitations, lineIterator)
    )

    return LdsOrdinance(type, date, templeCode, place, status, familyId, notes, sourceCitations)
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
    val phoneticVariations = mutableListOf<PersonalNameVariation>()
    val romanizedVariations = mutableListOf<PersonalNameVariation>()
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
        TagParser("FONE") { value -> phoneticVariations.add(parsePersonalNameVariation(value, lineIterator)) },
        TagParser("ROMN") { value -> romanizedVariations.add(parsePersonalNameVariation(value, lineIterator)) },
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
        phoneticVariations,
        romanizedVariations,
        notes,
        sourceCitations
    )
}

private fun parsePersonalNameVariation(name: String, lineIterator: LineIterator): PersonalNameVariation {
    var method: String? = null
    var prefix: String? = null
    var given: String? = null
    var nickname: String? = null
    var surnamePrefix: String? = null
    var surname: String? = null
    var suffix: String? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()

    lineIterator.parseByTag(
        TagParser("TYPE") { method = it },
        TagParser("NPFX") { prefix = it },
        TagParser("GIVN") { given = it },
        TagParser("NICK") { nickname = it },
        TagParser("SPFX") { surnamePrefix = it },
        TagParser("SURN") { surname = it },
        TagParser("NSFX") { suffix = it },
        noteParser(notes),
        sourceCitationParser(sourceCitations, lineIterator)
    )

    return PersonalNameVariation(
        name, method, prefix, given, nickname, surnamePrefix, surname, suffix, notes, sourceCitations
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
    val ldsSpouseSealings = mutableListOf<LdsOrdinance>()
    val submitterIds = mutableListOf<String>()
    var restriction: String? = null
    val references = mutableListOf<String>()
    var automatedRecordId: String? = null
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
        TagParser("EVEN", familyEventParser),
        TagParser("SLGS") { ldsSpouseSealings.add(parseLdsOrdinance("SLGS", lineIterator)) },
        TagParser("RESN") { restriction = it },
        TagParser("SUBM") { submitterIds.add(it) },
        TagParser("REFN") { references.add(it) },
        TagParser("RIN") { automatedRecordId = it },
        TagParser("CHAN") { changeDate = parseChangeDate(lineIterator) },
        noteParser(notes),
        sourceCitationParser(sourceCitations, lineIterator),
        multimediaLinkParser(multimediaLinks, lineIterator)
    )

    return FamilyGroup(
        id = FamilyGroupId(id),
        events = events,
        husbandId = husband,
        wifeId = wife,
        childrenIds = children,
        nbrOfChildren = nbrOfChildren,
        ldsSpouseSealings = ldsSpouseSealings,
        submitterIds = submitterIds,
        restriction = restriction,
        references = references,
        automatedRecordId = automatedRecordId,
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

// The EVENT_DETAIL substructure is shared by every event and attribute. This accumulator
// collects those fields as its tag parsers fire and builds the EventDetail, so each caller
// only adds its own extra tags.
private class EventDetailAccumulator {
    var type: String? = null
    var date: DateValue? = null
    var place: Place? = null
    private val address = AddressStructureAccumulator()
    var responsibleAgency: String? = null
    var religiousAffiliation: String? = null
    var cause: String? = null
    var restriction: String? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    fun tagParsers(lineIterator: LineIterator) = arrayOf(
        TagParser("TYPE") { type = it },
        dateParser { date = it },
        TagParser("PLAC") { name -> place = parsePlace(name, lineIterator) },
        TagParser("AGNC") { responsibleAgency = it },
        TagParser("RELI") { religiousAffiliation = it },
        TagParser("CAUS") { cause = it },
        TagParser("RESN") { restriction = it },
        noteParser(notes),
        sourceCitationParser(sourceCitations, lineIterator),
        multimediaLinkParser(multimediaLinks, lineIterator),
        *address.tagParsers(lineIterator)
    )

    fun build() = EventDetail(
        type, date, place, address.build(), responsibleAgency, religiousAffiliation, cause, restriction,
        notes, sourceCitations, multimediaLinks
    )
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
    var form: String? = null
    var latitude: Double? = null
    var longitude: Double? = null
    val phoneticVariations = mutableListOf<PlaceVariation>()
    val romanizedVariations = mutableListOf<PlaceVariation>()
    val notes = mutableListOf<String>()

    lineIterator.parseByTag(
        TagParser("FORM") { form = it },
        TagParser("MAP") { parseCoordinates(lineIterator, { longitude = it }, { latitude = it }) },
        TagParser("FONE") { value -> phoneticVariations.add(parsePlaceVariation(value, lineIterator)) },
        TagParser("ROMN") { value -> romanizedVariations.add(parsePlaceVariation(value, lineIterator)) },
        noteParser(notes)
    )

    return Place(
        name,
        form,
        longitude,
        latitude,
        phoneticVariations,
        romanizedVariations,
        notes.toList()
    )
}

private fun parsePlaceVariation(name: String, lineIterator: LineIterator): PlaceVariation {
    var method: String? = null

    lineIterator.parseByTag(
        TagParser("TYPE") { method = it }
    )

    return PlaceVariation(name, method)
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

// The GEDCOM ADDRESS_STRUCTURE is an optional ADDR block plus optional PHON/EMAIL/FAX/WWW
// lines that sit as siblings of ADDR (any of them may appear without an ADDR), so this
// accumulator contributes tag parsers to its enclosing structure rather than owning a subtree.
private class AddressStructureAccumulator {
    private var address1: String? = null
    private var address2: String? = null
    private var address3: String? = null
    private var city: String? = null
    private var state: String? = null
    private var postalCode: String? = null
    private var country: String? = null
    private var hasAddr = false
    private val phoneNumbers = mutableListOf<String>()
    private val emails = mutableListOf<String>()
    private val faxes = mutableListOf<String>()
    private val wwws = mutableListOf<String>()

    fun tagParsers(lineIterator: LineIterator) = arrayOf(
        TagParser("ADDR") { hasAddr = true; parseAddressBody(lineIterator) },
        TagParser("PHON") { phoneNumbers.add(it) },
        TagParser("EMAIL") { emails.add(it) },
        TagParser("FAX") { faxes.add(it) },
        TagParser("WWW") { wwws.add(it) }
    )

    private fun parseAddressBody(lineIterator: LineIterator) {
        lineIterator.parseByTag(
            TagParser("ADR1") { address1 = it },
            TagParser("ADR2") { address2 = it },
            TagParser("ADR3") { address3 = it },
            TagParser("CITY") { city = it },
            TagParser("STAE") { state = it },
            TagParser("POST") { postalCode = it },
            TagParser("CTRY") { country = it }
        )
    }

    fun build(): Address? {
        val empty = !hasAddr && phoneNumbers.isEmpty() && emails.isEmpty() && faxes.isEmpty() && wwws.isEmpty()
        if (empty) {
            return null
        }
        return Address(
            address1, address2, address3, city, state, postalCode, country,
            phoneNumbers, emails, faxes, wwws
        )
    }
}

private fun parseSourceCitation(value: String, lineIterator: LineIterator): SourceCitation {
    // 5.5.1 allows either a pointer to a SOUR record (@S1@) or an inline citation whose
    // descriptive text sits directly on the SOUR line.
    val pointer = isXref(value)
    val source = if (pointer) SourceId(value) else null
    var description: String? = if (pointer) null else value.ifEmpty { null }
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
        // In the inline form the transcribed source text sits directly under SOUR.
        TagParser("TEXT") { text -> description = listOfNotNull(description, text).joinToString("\n") },
        noteParser(notes),
        multimediaLinkParser(multimediaLinks, lineIterator),
        TagParser("QUAY") { quayValue ->
            qualityAssessment = QUAY.fromValue(quayValue.toInt())
            if (qualityAssessment == null) {
                logger.warn("Unrecognized QUAY value '$quayValue' for source citation '$value'")
            }
        }
    )

    return SourceCitation(
        source = source,
        description = description,
        page = page,
        eventTypeCitedFrom = eventTypeCitedFrom,
        data = data,
        notes = notes,
        multimediaLinks = multimediaLinks,
        qualityAssessment = qualityAssessment
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
    val repositoryCitations = mutableListOf<SourceRepositoryCitation>()
    val references = mutableListOf<String>()
    var automatedRecordId: String? = null
    var changeDate: LocalDate? = null
    val notes = mutableListOf<String>()
    val multimediaLinks = mutableListOf<MultimediaLink>()

    lineIterator.parseByTag(
        TagParser("DATA") { data = parseSourceData(lineIterator) },
        TagParser("AUTH") { author = it },
        TagParser("TITL") { title = it },
        TagParser("ABBR") { abbreviation = it },
        TagParser("PUBL") { publicationFacts = it },
        TagParser("TEXT") { text = it },
        TagParser("REPO") { repoId -> repositoryCitations.add(parseSourceRepositoryCitation(repoId, lineIterator)) },
        TagParser("REFN") { references.add(it) },
        TagParser("RIN") { automatedRecordId = it },
        TagParser("CHAN") { changeDate = parseChangeDate(lineIterator) },
        noteParser(notes),
        multimediaLinkParser(multimediaLinks, lineIterator)
    )

    return Source(
        id = SourceId(id),
        data = data,
        author = author,
        title = title,
        abbreviation = abbreviation,
        publicationFacts = publicationFacts,
        text = text,
        repositoryCitations = repositoryCitations,
        references = references,
        automatedRecordId = automatedRecordId,
        changeDate = changeDate,
        notes = notes,
        multimediaLinks = multimediaLinks
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

private fun parseSourceRepositoryCitation(repoId: String, lineIterator: LineIterator): SourceRepositoryCitation {
    val callNumbers = mutableListOf<String>()
    val notes = mutableListOf<String>()

    lineIterator.parseByTag(
        TagParser("CALN") { callNumbers.add(it) },
        noteParser(notes)
    )

    return SourceRepositoryCitation(repoId, callNumbers, notes)
}

private fun parseRepository(id: String, lineIterator: LineIterator): Repository {
    var name: String? = null
    val address = AddressStructureAccumulator()
    val references = mutableListOf<String>()
    var automatedRecordId: String? = null
    val notes = mutableListOf<String>()
    var changeDate: LocalDate? = null

    lineIterator.parseByTag(
        TagParser("NAME") { name = it },
        TagParser("REFN") { references.add(it) },
        TagParser("RIN") { automatedRecordId = it },
        noteParser(notes),
        TagParser("CHAN") { changeDate = parseChangeDate(lineIterator) },
        *address.tagParsers(lineIterator)
    )

    return Repository(id, name, address.build(), references, automatedRecordId, notes, changeDate)
}

private fun parseNoteRecord(id: String, lineIterator: LineIterator): NoteRecord {
    // The note's own text can sit directly on the level-0 line after the tag.
    val inlineText = lineIterator.current().content().removePrefix("NOTE").trimStart()
    val sourceCitations = mutableListOf<SourceCitation>()
    val references = mutableListOf<String>()
    var automatedRecordId: String? = null
    var changeDate: LocalDate? = null

    lineIterator.parseByTag(
        sourceCitationParser(sourceCitations, lineIterator),
        TagParser("REFN") { references.add(it) },
        TagParser("RIN") { automatedRecordId = it },
        TagParser("CHAN") { changeDate = parseChangeDate(lineIterator) }
    )

    return NoteRecord(id, inlineText, sourceCitations, references, automatedRecordId, changeDate)
}

private fun parseMultimediaRecord(id: String, lineIterator: LineIterator): MultimediaRecord {
    val files = mutableListOf<MultimediaFile>()
    var title: String? = null
    val references = mutableListOf<String>()
    var automatedRecordId: String? = null
    val notes = mutableListOf<String>()
    val sourceCitations = mutableListOf<SourceCitation>()
    var changeDate: LocalDate? = null

    lineIterator.parseByTag(
        TagParser("FILE") { reference -> files.add(parseMultimediaFile(reference, lineIterator)) },
        TagParser("TITL") { title = it },
        TagParser("REFN") { references.add(it) },
        TagParser("RIN") { automatedRecordId = it },
        noteParser(notes),
        sourceCitationParser(sourceCitations, lineIterator),
        TagParser("CHAN") { changeDate = parseChangeDate(lineIterator) }
    )

    return MultimediaRecord(id, files, title, references, automatedRecordId, notes, sourceCitations, changeDate)
}

private fun parseMultimediaFile(reference: String, lineIterator: LineIterator): MultimediaFile {
    var format: String? = null
    var mediaType: String? = null
    var title: String? = null

    lineIterator.parseByTag(
        // In 5.5.1 the media type (MEDI) is nested under the file's FORM.
        TagParser("FORM") { value ->
            format = value
            lineIterator.parseByTag(TagParser("MEDI") { mediaType = it })
        },
        TagParser("MEDI") { mediaType = it },
        TagParser("TITL") { title = it }
    )

    return MultimediaFile(reference, format, mediaType, title)
}

private fun parseSubmitter(id: String, lineIterator: LineIterator): Submitter {
    var name: String? = null
    val address = AddressStructureAccumulator()
    val languages = mutableListOf<String>()
    var registeredFileNumber: String? = null
    var automatedRecordId: String? = null
    val multimediaLinks = mutableListOf<MultimediaLink>()
    val notes = mutableListOf<String>()
    var changeDate: LocalDate? = null

    lineIterator.parseByTag(
        TagParser("NAME") { name = it },
        TagParser("LANG") { languages.add(it) },
        TagParser("RFN") { registeredFileNumber = it },
        TagParser("RIN") { automatedRecordId = it },
        multimediaLinkParser(multimediaLinks, lineIterator),
        noteParser(notes),
        TagParser("CHAN") { changeDate = parseChangeDate(lineIterator) },
        *address.tagParsers(lineIterator)
    )

    return Submitter(id, name, address.build(), languages, registeredFileNumber, automatedRecordId, multimediaLinks, notes, changeDate)
}

private fun parseSubmission(id: String, lineIterator: LineIterator): Submission {
    var submitterId: String? = null
    var familyFile: String? = null
    var templeCode: String? = null
    var ancestorGenerations: Int? = null
    var descendantGenerations: Int? = null
    var ordinanceProcessFlag: String? = null
    var automatedRecordId: String? = null
    val notes = mutableListOf<String>()

    lineIterator.parseByTag(
        TagParser("SUBM") { submitterId = it },
        TagParser("FAMF") { familyFile = it },
        TagParser("TEMP") { templeCode = it },
        TagParser("ANCE") { ancestorGenerations = it.toIntOrNull() },
        TagParser("DESC") { descendantGenerations = it.toIntOrNull() },
        TagParser("ORDI") { ordinanceProcessFlag = it },
        TagParser("RIN") { automatedRecordId = it },
        noteParser(notes)
    )

    return Submission(
        id, submitterId, familyFile, templeCode, ancestorGenerations, descendantGenerations,
        ordinanceProcessFlag, automatedRecordId, notes
    )
}

private fun parseHeader(lineIterator: LineIterator): Header {
    var source: Header.Source? = null
    var destination: String? = null
    var date: LocalDate? = null
    var time: String? = null
    var submitterId: String? = null
    var submissionId: String? = null
    var fileName: String? = null
    var copyright: String? = null
    var gedcomVersion: String? = null
    var gedcomForm: String? = null
    var characterSet: String? = null
    var characterSetVersion: String? = null
    var language: String? = null
    var placeForm: String? = null
    val notes = mutableListOf<String>()

    lineIterator.parseByTag(
        TagParser("SOUR") { systemId -> source = parseHeaderSource(systemId, lineIterator) },
        TagParser("DEST") { destination = it },
        TagParser("DATE") { value ->
            date = parseSimpleGregorianDate(value)
            lineIterator.parseByTag(TagParser("TIME") { time = it })
        },
        TagParser("SUBM") { submitterId = it },
        TagParser("SUBN") { submissionId = it },
        TagParser("FILE") { fileName = it },
        TagParser("COPR") { copyright = it },
        TagParser("GEDC") {
            lineIterator.parseByTag(
                TagParser("VERS") { gedcomVersion = it },
                TagParser("FORM") { gedcomForm = it }
            )
        },
        TagParser("CHAR") { value ->
            characterSet = value
            lineIterator.parseByTag(TagParser("VERS") { characterSetVersion = it })
        },
        TagParser("LANG") { language = it },
        TagParser("PLAC") {
            lineIterator.parseByTag(TagParser("FORM") { placeForm = it })
        },
        noteParser(notes)
    )

    return Header(
        source, destination, date, time, submitterId, submissionId, fileName, copyright,
        gedcomVersion, gedcomForm, characterSet, characterSetVersion, language, placeForm, notes
    )
}

private fun parseHeaderSource(systemId: String, lineIterator: LineIterator): Header.Source {
    var version: String? = null
    var productName: String? = null
    var corporation: String? = null

    lineIterator.parseByTag(
        TagParser("VERS") { version = it },
        TagParser("NAME") { productName = it },
        TagParser("CORP") { corporation = it }
    )

    return Header.Source(systemId, version, productName, corporation)
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
                ?: parseDatePhrase(dateString)
        }

private fun parseDatePhrase(dateString: String): DatePhrase {
    // A DATE_PHRASE is delimited by parentheses; strip them for the stored text.
    val trimmed = dateString.trim()
    return if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
        DatePhrase(trimmed.substring(1, trimmed.length - 1))
    } else {
        DatePhrase(dateString)
    }
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
    if (dateParts.firstOrNull() != "INT") {
        return null
    }

    val rest = dateParts.drop(1).joinToString(" ")
    val open = rest.indexOf('(')
    if (open < 0) {
        return null
    }

    val datePart = rest.substring(0, open).trim()
    val phrase = rest.substring(open + 1).trimEnd().removeSuffix(")")
    val date = parseDate(datePart.split(" ").filter(String::isNotEmpty)) ?: return null

    return DatePhraseExt(date, phrase)
}

private val yearRegex = """^(\d{3,4})(?: (BCE|BC|B\.C\.))?$""".toRegex()
private val monthYearRegex = """^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})$""".toRegex()
private val dayMonthYearRegex = """^(\d{1,2}) (JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})$""".toRegex()
private val dayMonthRegex = """^(\d{1,2}) (JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)$""".toRegex()
private val monthDualYearRegex = """^(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})/(\d{3,4})$""".toRegex()
private val dayMonthDualYearRegex =
    """^(\d{1,2}) (JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC) (\d{3,4})/(\d{3,4})$""".toRegex()

// A leading calendar escape such as @#DJULIAN@ (note @#DFRENCH R@ contains a space).
private val calendarEscapeRegex = """^(@#D[^@]*@)\s*""".toRegex()

private fun parseDate(dateParts: List<String>): Date? {
    val raw = dateParts.joinToString(" ")
    val escapeMatch = calendarEscapeRegex.find(raw)
    val calendar = escapeMatch?.groupValues?.get(1)?.let(Calendars::fromId) ?: Calendars.GREGORIAN
    val body = escapeMatch?.let { raw.substring(it.range.last + 1) } ?: raw

    val dateCalendar = when (calendar) {
        Calendars.GREGORIAN -> parseGregorian(body)
        Calendars.JULIAN -> parseJulian(body)
        Calendars.HEBREW -> parseHebrew(body)
        Calendars.FRENCH -> parseFrench(body)
        Calendars.UNKNOWN -> null
    } ?: return null

    return Date(calendar, dateCalendar)
}

private fun parseGregorian(body: String): GregorianCalendar? =
    body.parseByPattern(
        RegexParser(yearRegex) { (year, bc) ->
            GregorianCalendar(year = Year(year.toInt(), year.toInt()), beforeCommonEra = bc.isEmpty().not())
        },
        RegexParser(monthYearRegex) { (month, year) ->
            GregorianCalendar(
                month = GregorianCalendar.Month.valueOf(month),
                year = Year(year.toInt(), year.toInt())
            )
        },
        RegexParser(dayMonthYearRegex) { (day, month, year) ->
            GregorianCalendar(
                day = day.toInt(),
                month = GregorianCalendar.Month.valueOf(month),
                year = Year(year.toInt(), year.toInt())
            )
        },
        RegexParser(dayMonthRegex) { (day, month) ->
            GregorianCalendar(
                day = day.toInt(),
                month = GregorianCalendar.Month.valueOf(month)
            )
        },
        RegexParser(monthDualYearRegex) { (month, oldStyleYear, newStyleYear) ->
            GregorianCalendar(
                month = GregorianCalendar.Month.valueOf(month),
                year = Year(oldStyleYear.toInt(), newStyleYear.toInt())
            )
        },
        RegexParser(dayMonthDualYearRegex) { (day, month, oldStyleYear, newStyleYear) ->
            GregorianCalendar(
                day = day.toInt(),
                month = GregorianCalendar.Month.valueOf(month),
                year = Year(oldStyleYear.toInt(), newStyleYear.toInt())
            )
        }
    )

private val gregorianMonths = GregorianCalendar.Month.values().associateBy { it.name }
private val hebrewMonths = HebrewCalendar.Month.values().associateBy { it.name }
private val frenchMonths = FrenchCalendar.Month.values().associateBy { it.name }

private fun parseJulian(body: String): JulianCalendar? {
    val bce = body.trimEnd().let { it.endsWith(" BCE") || it.endsWith(" BC") || it.endsWith(" B.C.") }
    val cleaned = body.removeSuffix("BCE").removeSuffix("BC").removeSuffix("B.C.").trim()
    val (day, month, year) = tokenizeCalendarDate(cleaned, gregorianMonths) ?: return null
    return JulianCalendar(day, month, year, bce)
}

private fun parseHebrew(body: String): HebrewCalendar? {
    val (day, month, year) = tokenizeCalendarDate(body, hebrewMonths) ?: return null
    return HebrewCalendar(day, month, year)
}

private fun parseFrench(body: String): FrenchCalendar? {
    val (day, month, year) = tokenizeCalendarDate(body, frenchMonths) ?: return null
    return FrenchCalendar(day, month, year)
}

// GEDCOM dates read day/month/year in that order. This locates the month token (if any) and
// reads the day just before it and the year just after, so the same logic serves every
// non-Gregorian calendar by swapping the month name table.
private fun <M> tokenizeCalendarDate(body: String, months: Map<String, M>): Triple<Int?, M?, Int?>? {
    val tokens = body.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (tokens.isEmpty()) {
        return null
    }

    val monthIndex = tokens.indexOfFirst { months.containsKey(it.uppercase()) }
    if (monthIndex < 0) {
        val year = tokens.singleOrNull()?.substringBefore('/')?.toIntOrNull() ?: return null
        return Triple(null, null, year)
    }

    val month = months[tokens[monthIndex].uppercase()]
    val day = tokens.getOrNull(monthIndex - 1)?.toIntOrNull()
    val year = tokens.getOrNull(monthIndex + 1)?.substringBefore('/')?.toIntOrNull()
    return Triple(day, month, year)
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

private fun isXref(value: String) = value.length > 2 && value.startsWith("@") && value.endsWith("@")

data class Line(val lineNbr: Int, val line: String) {
    fun depth() = line.takeWhile(Char::notSpace).toInt()
    fun tag() = line.dropWhile(Char::notSpace).drop(1).takeWhile(Char::notSpace)

    // A literal "@" in a line value is escaped as "@@" in GEDCOM; pointers (@X@) and escape
    // sequences (@#...@) never contain a doubled "@", so collapsing "@@" is always safe here.
    fun content() = line.dropWhile(Char::notSpace).drop(1).dropWhile(Char::notSpace).drop(1).replace("@@", "@")

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
            { it.content().substringBefore(' ') },
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

    // Drains every line nested under the line most recently consumed by the caller, used to
    // discard a record we recognize structurally but do not model.
    fun skipChildren() {
        skipSubtree(current().depth())
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

private fun multimediaLinkParser(multimediaLinks: MutableList<MultimediaLink>, lineIterator: LineIterator) =
    TagParser("OBJE") { value -> multimediaLinks.add(parseMultimediaLink(value, lineIterator)) }

private fun parseMultimediaLink(value: String, lineIterator: LineIterator): MultimediaLink {
    if (isXref(value)) {
        return MultimediaReference(value)
    }

    // An embedded (5.5-style) multimedia link carries the object inline via FILE/FORM/TITL.
    val files = mutableListOf<MultimediaFile>()
    var title: String? = null
    var topLevelFormat: String? = null

    lineIterator.parseByTag(
        TagParser("FILE") { reference -> files.add(parseMultimediaFile(reference, lineIterator)) },
        TagParser("FORM") { topLevelFormat = it },
        TagParser("TITL") { title = it }
    )

    // 5.5 places FORM as a sibling of a single FILE rather than under it; fold it in.
    val resolvedFiles = if (topLevelFormat != null && files.size == 1 && files.single().format == null) {
        listOf(files.single().copy(format = topLevelFormat))
    } else {
        files
    }

    return EmbeddedMultimedia(resolvedFiles, title)
}

private fun sourceCitationParser(sourceCitations: MutableList<SourceCitation>, lineIterator: LineIterator) =
    TagParser("SOUR") { value -> sourceCitations.add(parseSourceCitation(value, lineIterator)) }

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
    var header: Header? = null,
    val familyGroups: MutableCollection<FamilyGroup> = mutableListOf(),
    val individuals: MutableCollection<Individual> = mutableListOf(),
    val sources: MutableList<Source> = mutableListOf(),
    val repositories: MutableList<Repository> = mutableListOf(),
    val noteRecords: MutableList<NoteRecord> = mutableListOf(),
    val multimediaRecords: MutableList<MultimediaRecord> = mutableListOf(),
    val submitters: MutableList<Submitter> = mutableListOf(),
    val submissions: MutableList<Submission> = mutableListOf()
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
