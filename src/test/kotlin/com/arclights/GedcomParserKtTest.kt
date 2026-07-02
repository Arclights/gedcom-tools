package com.arclights

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class GedcomParserKtTest {

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