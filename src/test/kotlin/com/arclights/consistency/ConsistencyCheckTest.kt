package com.arclights.consistency

import com.arclights.BirthEvent
import com.arclights.Calendars
import com.arclights.Date
import com.arclights.DeathEvent
import com.arclights.EventDetail
import com.arclights.FamilyEvent
import com.arclights.FamilyEventType
import com.arclights.FamilyGroup
import com.arclights.FamilyGroupId
import com.arclights.Gedcom
import com.arclights.GeneralIndividualEvent
import com.arclights.GregorianCalendar
import com.arclights.Individual
import com.arclights.IndividualEvent
import com.arclights.IndividualEventDetails
import com.arclights.IndividualId
import com.arclights.IndividualName
import com.arclights.Year
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ConsistencyCheckTest {

    private val today = LocalDate.of(2026, 7, 3)
    private val config = ConsistencyConfig(today = today)

    // --- Errors -------------------------------------------------------------

    @Test
    fun `flags death before birth as an error`() {
        val person = person("@I1@", "John /Doe/", birth = date(1950), death = date(1940))
        val issues = check(DeathBeforeBirthCheck, gedcom(person))

        assertThat(issues).singleElement().satisfies({
            assertThat(it.severity).isEqualTo(Severity.ERROR)
            assertThat(it.message).contains("John /Doe/", "death", "before birth")
        })
    }

    @Test
    fun `does not flag a normal life span`() {
        val person = person("@I1@", "John /Doe/", birth = date(1950), death = date(2020))
        assertThat(check(DeathBeforeBirthCheck, gedcom(person))).isEmpty()
    }

    @Test
    fun `flags a child older than their parent`() {
        val parent = person("@I1@", "Old /Doe/", birth = date(1980))
        val child = person("@I2@", "Young /Doe/", birth = date(1970))
        val family = family("@F1@", husband = parent.id, children = listOf(child.id))

        val issues = check(ChildOlderThanParentCheck, gedcom(listOf(parent, child), family))

        assertThat(issues).singleElement().satisfies({
            assertThat(it.severity).isEqualTo(Severity.ERROR)
            assertThat(it.message).contains("Young /Doe/", "before their parent", "Old /Doe/")
        })
    }

    @Test
    fun `flags a child born well after the father died but allows a posthumous birth`() {
        val father = person("@I1@", "Dad /Doe/", death = date(2000, 1, 1))
        val posthumous = person("@I2@", "Posthumous /Doe/", birth = date(2000, 5, 1))
        val tooLate = person("@I3@", "Late /Doe/", birth = date(2001, 6, 1))
        val family = family("@F1@", husband = father.id, children = listOf(posthumous.id, tooLate.id))

        val issues = check(ChildBornAfterParentDeathCheck, gedcom(listOf(father, posthumous, tooLate), family))

        assertThat(issues).singleElement().satisfies({
            assertThat(it.message).contains("Late /Doe/", "after the death of their father")
        })
    }

    @Test
    fun `flags any child born after the mother died`() {
        val mother = person("@I1@", "Mum /Doe/", death = date(2000, 1, 1))
        val child = person("@I2@", "Kid /Doe/", birth = date(2000, 5, 1))
        val family = family("@F1@", wife = mother.id, children = listOf(child.id))

        val issues = check(ChildBornAfterParentDeathCheck, gedcom(listOf(mother, child), family))

        assertThat(issues).singleElement().satisfies({
            assertThat(it.message).contains("Kid /Doe/", "after the death of their mother")
        })
    }

    // --- Age warnings -------------------------------------------------------

    @Test
    fun `flags an implausibly old age at death`() {
        val person = person("@I1@", "Methuselah", birth = date(1800), death = date(1950))
        val issues = check(DiedTooOldCheck, gedcom(person))

        assertThat(issues).singleElement().satisfies({
            assertThat(it.severity).isEqualTo(Severity.WARNING)
            assertThat(it.message).contains("died at age 150")
        })
    }

    @Test
    fun `flags a living person that is too old`() {
        val person = person("@I1@", "Ancient", birth = date(1850))
        assertThat(check(AliveTooOldCheck, gedcom(person)))
            .singleElement()
            .satisfies({ assertThat(it.message).contains("176 years old today") })
    }

    @Test
    fun `does not flag a living person once a death is recorded`() {
        val person = person("@I1@", "Ancient", birth = date(1850), death = date(1900))
        assertThat(check(AliveTooOldCheck, gedcom(person))).isEmpty()
    }

    @Test
    fun `flags a parent that was too young and too old`() {
        val young = person("@I1@", "Young Parent", birth = date(2010))
        val old = person("@I2@", "Old Parent", birth = date(1900))
        val child = person("@I3@", "Child", birth = date(2020))
        val family = family("@F1@", husband = old.id, wife = young.id, children = listOf(child.id))
        val g = gedcom(listOf(young, old, child), family)

        assertThat(check(ParentTooYoungCheck, g)).singleElement()
            .satisfies({ assertThat(it.message).contains("Young Parent", "only 10 years old") })
        assertThat(check(ParentTooOldCheck, g)).singleElement()
            .satisfies({ assertThat(it.message).contains("Old Parent", "120 years old") })
    }

    @Test
    fun `flags events before birth and after death`() {
        val person = person(
            "@I1@", "John /Doe/",
            birth = date(1950),
            death = date(2000),
            extraEvents = listOf(
                generalEvent("RESI", date(1940)),
                generalEvent("RESI", date(2010))
            )
        )
        val g = gedcom(person)

        assertThat(check(EventBeforeBirthCheck, g)).singleElement()
            .satisfies({ assertThat(it.message).contains("RESI", "before their birth") })
        assertThat(check(EventAfterDeathCheck, g)).singleElement()
            .satisfies({ assertThat(it.message).contains("RESI", "after their death") })
    }

    // --- Family warnings ----------------------------------------------------

    @Test
    fun `flags siblings too close in age but not twins`() {
        val twinA = person("@I1@", "Twin A", birth = date(2000, 5, 1))
        val twinB = person("@I2@", "Twin B", birth = date(2000, 5, 1))
        val tooClose = person("@I3@", "Too Close", birth = date(2000, 8, 1))
        val family = family("@F1@", children = listOf(twinA.id, twinB.id, tooClose.id))

        val issues = check(SiblingsTooCloseCheck, gedcom(listOf(twinA, twinB, tooClose), family))

        assertThat(issues).singleElement()
            .satisfies({ assertThat(it.message).contains("Twin B", "Too Close", "too close") })
    }

    @Test
    fun `flags a large spouse age gap`() {
        val husband = person("@I1@", "Old", birth = date(1900))
        val wife = person("@I2@", "Young", birth = date(1960))
        val family = family("@F1@", husband = husband.id, wife = wife.id)

        assertThat(check(SpouseAgeGapCheck, gedcom(listOf(husband, wife), family)))
            .singleElement()
            .satisfies({ assertThat(it.message).contains("age gap of 60 years") })
    }

    @Test
    fun `flags marrying too young`() {
        val husband = person("@I1@", "Groom", birth = date(1990))
        val wife = person("@I2@", "Bride", birth = date(2005))
        val family = family(
            "@F1@", husband = husband.id, wife = wife.id,
            events = listOf(marriage(date(2015)))
        )

        assertThat(check(MarriedTooYoungCheck, gedcom(listOf(husband, wife), family)))
            .singleElement()
            .satisfies({ assertThat(it.message).contains("Bride", "only 10 years old") })
    }

    @Test
    fun `flags several marriages for the same couple`() {
        val husband = person("@I1@", "H")
        val wife = person("@I2@", "W")
        val f1 = family("@F1@", husband = husband.id, wife = wife.id, events = listOf(marriage(date(1950))))
        val f2 = family("@F2@", husband = husband.id, wife = wife.id, events = listOf(marriage(date(1960))))
        val g = Gedcom(
            individuals = listOf(husband, wife).associateBy { it.id },
            familyGroups = listOf(f1, f2).associateBy { it.id }
        )

        assertThat(check(MultipleMarriagesCheck, g)).singleElement()
            .satisfies({ assertThat(it.message).contains("2 marriages recorded") })
    }

    @Test
    fun `flags an unconnected person only`() {
        val a = person("@I1@", "Connected A")
        val b = person("@I2@", "Connected B")
        val lonely = person("@I3@", "Lonely")
        val family = family("@F1@", husband = a.id, wife = b.id)

        assertThat(check(UnconnectedPersonCheck, gedcom(listOf(a, b, lonely), family)))
            .singleElement()
            .satisfies({ assertThat(it.message).contains("Lonely", "not connected") })
    }

    // --- Name warnings ------------------------------------------------------

    @Test
    fun `flags a married name entered as a maiden name`() {
        val husband = person("@I1@", "John /Smith/")
        val wife = person("@I2@", "Jane /Smith/")
        val family = family("@F1@", husband = husband.id, wife = wife.id)

        assertThat(check(MarriedNameAsMaidenCheck, gedcom(listOf(husband, wife), family)))
            .singleElement()
            .satisfies({ assertThat(it.message).contains("Jane /Smith/", "Smith") })
    }

    @Test
    fun `flags titles in given name and surname`() {
        val doctor = person("@I1@", "Dr John /Doe/")
        val revSurname = Individual(
            id = IndividualId("@I2@"),
            names = listOf(IndividualName(name = "John /Rev Doe/"))
        )

        assertThat(check(TitleInGivenNameCheck, gedcom(doctor))).singleElement()
            .satisfies({ assertThat(it.message).contains("title ('Dr')", "given name") })
        assertThat(check(TitleInSurnameCheck, gedcom(revSurname))).singleElement()
            .satisfies({ assertThat(it.message).contains("title ('Rev')", "surname") })
    }

    @Test
    fun `does not flag a standalone surname that happens to be a title word`() {
        val king = person("@I1@", "Martin /King/")
        assertThat(check(TitleInSurnameCheck, gedcom(king))).isEmpty()
    }

    @Test
    fun `flags several birth and death events`() {
        val person = Individual(
            id = IndividualId("@I1@"),
            names = listOf(IndividualName("John /Doe/")),
            events = listOf(
                birthEvent(date(1950)), birthEvent(date(1951)),
                deathEvent(date(2000)), deathEvent(date(2001))
            )
        )
        val g = gedcom(person)

        assertThat(check(MultipleBirthEventsCheck, g)).singleElement()
            .satisfies({ assertThat(it.message).contains("2 birth events") })
        assertThat(check(MultipleDeathEventsCheck, g)).singleElement()
            .satisfies({ assertThat(it.message).contains("2 death events") })
    }

    @Test
    fun `flags a date in the future`() {
        val person = person("@I1@", "Time Traveller", birth = date(2050))
        assertThat(check(FutureDateCheck, gedcom(person))).singleElement()
            .satisfies({ assertThat(it.message).contains("Birth event dated in the future") })
    }

    @Test
    fun `runConsistencyChecks orders errors before warnings`() {
        val person = person("@I1@", "John /Doe/", birth = date(1950), death = date(1940))
        val issues = runConsistencyChecks(gedcom(person), config)

        assertThat(issues).isNotEmpty
        assertThat(issues.first().severity).isEqualTo(Severity.ERROR)
    }

    // --- Helpers ------------------------------------------------------------

    private fun check(check: ConsistencyCheck, gedcom: Gedcom) =
        check.findings(gedcom, config).map { Issue(check.severity, check.name, it) }

    private fun date(year: Int, month: Int = 1, day: Int = 1) = Date(
        Calendars.GREGORIAN,
        GregorianCalendar(
            day = day,
            month = GregorianCalendar.Month.entries[month - 1],
            year = Year(year, year)
        )
    )

    private fun birthEvent(date: Date) = BirthEvent(
        details = IndividualEventDetails(EventDetail(date = date), age = null)
    )

    private fun deathEvent(date: Date) = DeathEvent(
        confirmed = true,
        details = IndividualEventDetails(EventDetail(date = date), age = null)
    )

    private fun generalEvent(type: String, date: Date) = GeneralIndividualEvent(
        type = type,
        details = IndividualEventDetails(EventDetail(date = date), age = null)
    )

    private fun marriage(date: Date) = FamilyEvent(
        eventType = FamilyEventType.MARRIAGE,
        detail = com.arclights.FamilyEventDetail(detail = EventDetail(date = date))
    )

    private fun person(
        id: String,
        name: String,
        birth: Date? = null,
        death: Date? = null,
        extraEvents: List<IndividualEvent> = emptyList()
    ) = Individual(
        id = IndividualId(id),
        names = listOf(IndividualName(name)),
        events = listOfNotNull(
            birth?.let(::birthEvent),
            death?.let(::deathEvent)
        ) + extraEvents
    )

    private fun family(
        id: String,
        husband: IndividualId? = null,
        wife: IndividualId? = null,
        children: List<IndividualId> = emptyList(),
        events: List<FamilyEvent> = emptyList()
    ) = FamilyGroup(
        id = FamilyGroupId(id),
        husbandId = husband,
        wifeId = wife,
        childrenIds = children,
        events = events
    )

    private fun gedcom(person: Individual) = Gedcom(individuals = mapOf(person.id to person))

    private fun gedcom(individuals: List<Individual>, vararg families: FamilyGroup) = Gedcom(
        individuals = individuals.associateBy { it.id },
        familyGroups = families.associateBy { it.id }
    )
}
