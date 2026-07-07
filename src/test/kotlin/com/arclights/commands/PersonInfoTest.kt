package com.arclights.commands

import com.arclights.Association
import com.arclights.BirthEvent
import com.arclights.Calendars
import com.arclights.ChildToFamilyLink
import com.arclights.Date
import com.arclights.DeathEvent
import com.arclights.EventDetail
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
import com.arclights.Source
import com.arclights.SourceCitation
import com.arclights.SourceId
import com.arclights.SpouseToFamilyLink
import com.arclights.Year
import com.arclights.findPeopleByName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PersonInfoTest {

    @Test
    fun rendersInfoForPerson() {
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
                        details = EventDetail(
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
        val lines = personInfoText(gedcom, person).normalizedLines()

        // Then
        assertThat(lines).contains("John Doe", "♂ Male · b. 1947")
        assertThat(lines).containsSequence("Events", "Birth 5 AUG 1947 · Vallkärra (M)")
        assertThat(lines).containsSequence("Family", "Parents Richard Doe & Mary Doe", "Spouse Jane Doe")
        assertThat(lines).containsSequence("Notes", "• A note")
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
        val lines = personInfoText(gedcom, person).normalizedLines()

        // Then
        assertThat(lines).containsSequence(
            "Parents unknown (family @F1@)",
            "Spouse unknown (family @F2@)"
        )
    }

    @Test
    fun rendersNicknameCauseAgeAssociationsSourcesAndIdentifiers() {
        // Given
        val godfather = Individual(id = IndividualId("@I9@"), names = listOf(IndividualName("Karl /Berg/")))
        val person = Individual(
            id = IndividualId("@I1@"),
            names = listOf(IndividualName("John /Doe/", nickname = "Johnny")),
            sex = Sex.MALE,
            events = listOf(
                DeathEvent(
                    confirmed = true,
                    details = IndividualEventDetails(
                        details = EventDetail(
                            date = Date(
                                Calendars.GREGORIAN,
                                GregorianCalendar(day = 3, month = GregorianCalendar.Month.JAN, year = Year(1998, 1998))
                            ),
                            place = Place(name = "Lund"),
                            cause = "Pneumonia"
                        ),
                        age = "50y"
                    )
                )
            ),
            associations = listOf(Association(individualId = godfather.id, relation = "Godfather")),
            references = listOf("REF-42"),
            sourceCitations = listOf(SourceCitation(source = SourceId("@S1@"), page = "17"))
        )
        val gedcom = Gedcom(
            individuals = listOf(person, godfather).associateBy { it.id },
            sources = mapOf(SourceId("@S1@") to Source(id = SourceId("@S1@"), title = "Parish register"))
        )

        // When
        val lines = personInfoText(gedcom, person).normalizedLines()

        // Then
        assertThat(lines).contains("John Doe \"Johnny\"")
        assertThat(lines).containsSequence("Events", "Death 3 JAN 1998 · Lund · Pneumonia · aged 50y")
        assertThat(lines).containsSequence("Associations", "Godfather Karl Berg")
        assertThat(lines).containsSequence("Sources", "• Parish register, p. 17")
        assertThat(lines).containsSequence("Identifiers", "ID @I1@", "Reference REF-42")
    }

    @Test
    fun findsNoPeopleWhenNothingMatches() {
        val gedcom = Gedcom()

        assertThat(gedcom.findPeopleByName("jane")).isEmpty()
    }

    @Test
    fun findsAllPeopleMatchingNameCaseInsensitively() {
        val person1 = Individual(id = IndividualId("@I1@"), names = listOf(IndividualName("John /Doe/")))
        val person2 = Individual(id = IndividualId("@I2@"), names = listOf(IndividualName("Johnny /Doe/")))
        val other = Individual(id = IndividualId("@I3@"), names = listOf(IndividualName("Jane /Roe/")))
        val gedcom = Gedcom(individuals = listOf(person1, person2, other).associateBy { it.id })

        assertThat(gedcom.findPeopleByName("JOHN"))
            .containsExactlyInAnyOrder(person1, person2)
    }

    /** Strips color escapes and collapses indentation/padding so assertions ignore exact spacing. */
    private fun String.normalizedLines(): List<String> =
        replace(Regex("\\[[0-9;:]*m"), "")
            .lines()
            .map { it.trim().replace(Regex(" +"), " ") }
}
