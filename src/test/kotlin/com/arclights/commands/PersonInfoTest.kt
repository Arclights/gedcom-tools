package com.arclights.commands

import com.arclights.BirthEvent
import com.arclights.Calendars
import com.arclights.ChildToFamilyLink
import com.arclights.Date
import com.arclights.EvenDetail
import com.arclights.FamilyGroup
import com.arclights.FamilyGroupId
import com.arclights.Gedcom
import com.arclights.GregorianCalendar
import com.arclights.Individual
import com.arclights.IndividualEventDetails
import com.arclights.IndividualId
import com.arclights.IndividualName
import com.arclights.Place
import com.arclights.Sex
import com.arclights.SpouseToFamilyLink
import com.arclights.Year
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class PersonInfoTest {

    private val originalIn = System.`in`
    private val originalOut = System.out

    @AfterEach
    fun restoreStreams() {
        System.setIn(originalIn)
        System.setOut(originalOut)
    }

    private fun runCommand(gedcom: Gedcom, input: String): String {
        System.setIn(ByteArrayInputStream(input.toByteArray()))
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))

        PersonInfo().run(gedcom)

        return outputStream.toString()
    }

    @Test
    fun printsInfoForMatchedPerson() {
        // Given
        val father = Individual(id = IndividualId("@I2@"), names = listOf(IndividualName("Richard /Doe/")))
        val mother = Individual(id = IndividualId("@I3@"), names = listOf(IndividualName("Mary /Doe/")))
        val spouse = Individual(id = IndividualId("@I4@"), names = listOf(IndividualName("Jane /Doe/")))
        val person = Individual(
            id = IndividualId("@I1@"),
            names = listOf(IndividualName("John /Doe/")),
            sex = Sex.MALE,
            events = listOf(
                BirthEvent(
                    details = IndividualEventDetails(
                        details = EvenDetail(
                            date = Date(
                                Calendars.GREGORIAN,
                                GregorianCalendar(
                                    day = 5,
                                    month = GregorianCalendar.Month.AUG,
                                    year = Year(1947, 1947)
                                )
                            ),
                            place = Place(name = "Vallkärra (M)")
                        ),
                        age = null
                    )
                )
            ),
            notes = listOf("A note"),
            childToFamilies = listOf(ChildToFamilyLink(FamilyGroupId("@F1@"))),
            spouseToFamilies = listOf(SpouseToFamilyLink(FamilyGroupId("@F2@")))
        )
        val gedcom = Gedcom(
            individuals = listOf(person, father, mother, spouse).associateBy { it.id },
            familyGroups = mapOf(
                FamilyGroupId("@F1@") to FamilyGroup(
                    id = FamilyGroupId("@F1@"),
                    husbandId = father.id,
                    wifeId = mother.id,
                    childrenIds = listOf(person.id)
                ),
                FamilyGroupId("@F2@") to FamilyGroup(
                    id = FamilyGroupId("@F2@"),
                    husbandId = person.id,
                    wifeId = spouse.id
                )
            )
        )

        // When
        val output = runCommand(gedcom, "john\n")

        // Then
        assertThat(output.lines()).containsSequence(
            "Names:",
            "  John /Doe/",
            "Sex: MALE",
            "Birth: 5 AUG 1947 in Vallkärra (M)",
            "Notes:",
            "  A note",
            "Child of Richard /Doe/ and Mary /Doe/",
            "Spouse of Jane /Doe/"
        )
    }

    @Test
    fun fallsBackToFamilyIdWhenFamilyOrParentsAreUnknown() {
        // Given
        val person = Individual(
            id = IndividualId("@I1@"),
            names = listOf(IndividualName("John /Doe/")),
            childToFamilies = listOf(ChildToFamilyLink(FamilyGroupId("@F1@"))),
            spouseToFamilies = listOf(SpouseToFamilyLink(FamilyGroupId("@F2@")))
        )
        val gedcom = Gedcom(individuals = mapOf(person.id to person))

        // When
        val output = runCommand(gedcom, "john\n")

        // Then
        assertThat(output.lines()).containsSequence(
            "Child in family: @F1@",
            "Spouse in family: @F2@"
        )
    }

    @Test
    fun printsMessageWhenNoPersonMatches() {
        // Given
        val gedcom = Gedcom()

        // When
        val output = runCommand(gedcom, "jane\n")

        // Then
        assertThat(output).contains("Found no person with name 'jane'")
    }

    @Test
    fun disambiguatesBetweenMultipleMatches() {
        // Given
        val person1 = Individual(id = IndividualId("@I1@"), names = listOf(IndividualName("John /Doe/")))
        val person2 = Individual(id = IndividualId("@I2@"), names = listOf(IndividualName("Johnny /Doe/")))
        val gedcom = Gedcom(individuals = mapOf(person1.id to person1, person2.id to person2))

        // When
        val output = runCommand(gedcom, "john\n1\n")

        // Then
        assertThat(output).contains("Found multiple matches for name 'john':")
        assertThat(output).contains("Found Johnny /Doe/")
    }
}
