package com.arclights

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
import java.util.stream.Stream

class GedcomParserKtTest {

    private fun captureLogs(loggerName: String, block: () -> Unit): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(loggerName) as ch.qos.logback.classic.Logger
        val originalLevel = logger.level
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        logger.level = Level.ALL

        try {
            block()
        } finally {
            logger.detachAppender(appender)
            logger.level = originalLevel
        }

        return appender.list
    }

    @ParameterizedTest
    @MethodSource
    fun canParseFamily(data: String, expected: Gedcom) {
        // Given
        val input = data.trimIndent().lines()

        // When
        val actual = parseGedcom(input)

        // Then
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
    }

    @Test
    fun canParseIndividualAttributes() {
        // Given
        val input = """
            00 HEAD
            0 @I1@ INDI
            1 NAME John /Doe/
            1 OCCU Farmer
            2 PLAC Springfield
            1 TITL Reverend
        """.trimIndent().lines()

        // When
        val actual = parseGedcom(input)

        // Then
        val individual = actual.individuals.getValue(IndividualId("@I1@"))
        assertThat(individual.attributes).containsExactlyInAnyOrder(
            GeneralIndividualAttribute("OCCU", "Farmer", EvenDetail(place = Place("Springfield"))),
            GeneralIndividualAttribute("TITL", "Reverend", EvenDetail())
        )
    }

    @Test
    fun canParseAssociations() {
        // Given
        val input = """
            00 HEAD
            0 @I1@ INDI
            1 NAME John /Doe/
            1 ASSO @I2@
            2 RELA Godfather
            2 NOTE trusted family friend
        """.trimIndent().lines()

        // When
        val actual = parseGedcom(input)

        // Then
        val individual = actual.individuals.getValue(IndividualId("@I1@"))
        assertThat(individual.associations).containsExactly(
            Association(IndividualId("@I2@"), relation = "Godfather", notes = listOf("trusted family friend"))
        )
    }

    @Test
    fun canParseHusbandAndWifeAgeAtFamilyEvent() {
        // Given
        val input = """
            00 HEAD
            0 @F1@ FAM
            1 MARR
            2 HUSB
            3 AGE 26y
            2 WIFE
            3 AGE 24y
        """.trimIndent().lines()

        // When
        val actual = parseGedcom(input)

        // Then
        val detail = actual.familyGroups.getValue(FamilyGroupId("@F1@")).events.single().detail
        assertThat(detail?.husbandAge).isEqualTo("26y")
        assertThat(detail?.wifeAge).isEqualTo("24y")
    }

    @Test
    fun childrenOfAnUnrecognizedTagAreNotAttributedToTheParentRecord() {
        // Given
        // "_CUSTOM" is not a tag the individual parser recognizes. Its nested NOTE
        // belongs to that unknown subtree, not to the individual itself.
        val input = """
            00 HEAD
            0 @I1@ INDI
            1 NAME John /Doe/
            1 _CUSTOM somevalue
            2 NOTE this note belongs to _CUSTOM, not to the individual
        """.trimIndent().lines()

        // When
        val actual = parseGedcom(input)

        // Then
        val individual = actual.individuals.getValue(IndividualId("@I1@"))
        assertThat(individual.notes).isEmpty()
    }

    @Test
    fun contStartsANewLineButConcDoesNot() {
        // Given
        val input = """
            00 HEAD
            0 @I1@ INDI
            1 NAME John /Doe/
            1 NOTE first paragraph
            2 CONT second paragraph
            2 CONC , continued
        """.trimIndent().lines()

        // When
        val actual = parseGedcom(input)

        // Then
        val individual = actual.individuals.getValue(IndividualId("@I1@"))
        assertThat(individual.notes.single()).isEqualTo("first paragraph\nsecond paragraph, continued")
    }

    @Test
    fun stripByteOrderMarkOnlyStripsWhenABomIsActuallyPresent() {
        // Given
        val bom = "﻿"
        val withoutBom = listOf("0 HEAD", "0 TRLR")
        val withBom = listOf(bom + "0 HEAD", "0 TRLR")

        // Then
        assertThat(stripByteOrderMark(withoutBom)).isEqualTo(listOf("0 HEAD", "0 TRLR"))
        assertThat(stripByteOrderMark(withBom)).isEqualTo(listOf("0 HEAD", "0 TRLR"))
    }

    @Test
    fun logsAWarningWhenTwoIndividualsShareTheSameId() {
        // Given
        val input = """
            00 HEAD
            0 @I1@ INDI
            1 NAME John /Doe/
            0 @I1@ INDI
            1 NAME Jane /Doe/
        """.trimIndent().lines()

        // When
        val logs = captureLogs("GedcomParser") { parseGedcom(input) }

        // Then
        assertThat(logs).anyMatch { it.level == Level.WARN && it.formattedMessage.contains("@I1@") }
    }

    @ParameterizedTest
    @MethodSource
    fun canParseApproximateRangeAndPeriodDates(dateString: String, expected: DateValue) {
        // When
        val actual = parseDateValue(dateString)

        // Then
        assertThat(actual).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource
    fun mergeOrphanedLinesMergesLinesWithoutALevelNumberIntoThePreviousLine(
        input: List<String>,
        expected: List<String>
    ) {
        // When
        val actual = mergeOrphanedLines(input)

        // Then
        assertThat(actual).containsExactlyElementsOf(expected)
    }

    companion object {
        @JvmStatic
        fun canParseFamily(): Stream<Arguments> = Stream.of(
            Arguments.of(
                """
                    00 HEAD
                    0 @F500590@ FAM
                    1 _UPD 27 NOV 2022 16:34:07 GMT -0600
                    1 HUSB @I501515@
                    1 WIFE @I502070@
                    1 CHIL @I502072@
                    1 CHIL @I502069@
                    1 CHIL @I502073@
                    1 CHIL @I502074@
                    1 CHIL @I502075@
                    1 RIN MH:F500590
                    1 _UID 6383E5DFC3BDE066311B41E82D8267D9
                    1 MARR
                    2 DATE 27 NOV 1911
                    2 PLAC Vindelns prästgård, Degerfors kommun, Sverige
                    2 NOTE Make/Maka: Johan Sandström
                """,
                Gedcom(
                    familyGroups = mapOf(
                        FamilyGroupId("@F500590@") to FamilyGroup(
                            id = FamilyGroupId("@F500590@"),
                            husbandId = IndividualId("@I501515@"),
                            wifeId = IndividualId("@I502070@"),
                            childrenIds = listOf(
                                IndividualId("@I502072@"),
                                IndividualId("@I502069@"),
                                IndividualId("@I502073@"),
                                IndividualId("@I502074@"),
                                IndividualId("@I502075@")
                            ),
                            events = listOf(
                                FamilyEvent(
                                    FamilyEventType.MARRIAGE,
                                    FamilyEventDetail(
                                        detail = EvenDetail(
                                            date = Date(
                                                Calendars.GREGORIAN,
                                                GregorianCalendar(
                                                    day = 27,
                                                    month = GregorianCalendar.Month.NOV,
                                                    year = Year(1911, 1911)
                                                )
                                            ),
                                            place = Place(
                                                name = "Vindelns prästgård, Degerfors kommun, Sverige"
                                            ),
                                            notes = listOf(
                                                "Make/Maka: Johan Sandström"
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            ),
            Arguments.of(
                """
                    00 HEAD
                    0 @F500597@ FAM
                    1 _UPD 28 NOV 2022 15:33:37 GMT -0600
                    1 HUSB @I502105@
                    1 WIFE @I502103@
                    1 CHIL @I502106@
                    1 RIN MH:F500597
                    1 _UID 6385293106DA241FE8375A58377B208D
                    1 MARR
                """.trimIndent(),
                Gedcom(
                    familyGroups = mapOf(
                        FamilyGroupId("@F500597@") to FamilyGroup(
                            id = FamilyGroupId("@F500597@"),
                            husbandId = IndividualId("@I502105@"),
                            wifeId = IndividualId("@I502103@"),
                            childrenIds = listOf(
                                IndividualId("@I502106@")
                            ),
                            events = listOf(
                                FamilyEvent(
                                    eventType = FamilyEventType.MARRIAGE,
                                    detail = FamilyEventDetail(
                                        detail = EvenDetail()
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        @JvmStatic
        fun canParseApproximateRangeAndPeriodDates(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "ABT 1850",
                DateApproximated(Date(Calendars.GREGORIAN, GregorianCalendar(year = Year(1850, 1850))))
            ),
            Arguments.of(
                "BEF 1 JAN 1900",
                DateRangeBefore(
                    Date(
                        Calendars.GREGORIAN,
                        GregorianCalendar(day = 1, month = GregorianCalendar.Month.JAN, year = Year(1900, 1900))
                    )
                )
            ),
            Arguments.of(
                "AFT 1920",
                DateRangeAfter(Date(Calendars.GREGORIAN, GregorianCalendar(year = Year(1920, 1920))))
            ),
            Arguments.of(
                "BET 1920 AND 1925",
                DateRangeBetween(
                    Date(Calendars.GREGORIAN, GregorianCalendar(year = Year(1920, 1920))),
                    Date(Calendars.GREGORIAN, GregorianCalendar(year = Year(1925, 1925)))
                )
            ),
            Arguments.of(
                "FROM 1 JAN 1920 TO 31 DEC 1925",
                DatePeriod(
                    Date(
                        Calendars.GREGORIAN,
                        GregorianCalendar(day = 1, month = GregorianCalendar.Month.JAN, year = Year(1920, 1920))
                    ),
                    Date(
                        Calendars.GREGORIAN,
                        GregorianCalendar(day = 31, month = GregorianCalendar.Month.DEC, year = Year(1925, 1925))
                    )
                )
            )
        )

        @JvmStatic
        fun mergeOrphanedLinesMergesLinesWithoutALevelNumberIntoThePreviousLine(): Stream<Arguments> = Stream.of(
            Arguments.of(
                listOf("0 HEAD", "1 NOTE Some text", "1 RIN abc"),
                listOf("0 HEAD", "1 NOTE Some text", "1 RIN abc")
            ),
            Arguments.of(
                listOf("0 HEAD", "1 NOTE <p>Nils text</p>", "<p>Vid nitton ", "2 CONC års ålder"),
                listOf("0 HEAD", "1 NOTE <p>Nils text</p><p>Vid nitton ", "2 CONC års ålder")
            ),
            Arguments.of(
                listOf("0 HEAD", "1 NOTE first", "orphan one", "orphan two", "1 RIN abc"),
                listOf("0 HEAD", "1 NOTE firstorphan oneorphan two", "1 RIN abc")
            ),
            Arguments.of(
                listOf("-1", "0 HEAD"),
                listOf("-1", "0 HEAD")
            )
        )
    }
}