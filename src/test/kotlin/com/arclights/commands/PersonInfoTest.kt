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
        val output = personInfoText(gedcom, person)

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
        val output = personInfoText(gedcom, person)

        // Then
        assertThat(output.lines()).containsSequence(
            "Child in family: @F1@",
            "Spouse in family: @F2@"
        )
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
}
