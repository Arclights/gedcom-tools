package com.arclights.commands

import com.arclights.BirthEvent
import com.arclights.Color
import com.arclights.ColoredString
import com.arclights.DeathEvent
import com.arclights.Individual
import com.arclights.IndividualId
import com.arclights.IndividualName
import com.arclights.MultiLineEntity
import com.arclights.PrintMatrix
import com.arclights.SingleLineEntity.Companion.horizontalConnection
import com.arclights.SingleLineEntity.Companion.verticalConnection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GradeRelationshipTest {
    @Test
    fun canBuildCorrectMatrix() {
        // Given
        val rp1 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.UNKNOWN,
            Individual(id = IndividualId("1"), names = listOf(IndividualName("person1")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )
        val rp2 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARENT,
            Individual(id = IndividualId("2"), names = listOf(IndividualName("person2")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )
        val rp3 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARENT,
            Individual(id = IndividualId("3"), names = listOf(IndividualName("person3")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )
        val rp4 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARTNER,
            Individual(id = IndividualId("4"), names = listOf(IndividualName("person4")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )
        val rp5 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("5"), names = listOf(IndividualName("person5")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )
        val rp6 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARTNER,
            Individual(id = IndividualId("6"), names = listOf(IndividualName("person6")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )
        val rp7 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARENT,
            Individual(id = IndividualId("7"), names = listOf(IndividualName("person7")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )
        val rp8 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARENT,
            Individual(id = IndividualId("8"), names = listOf(IndividualName("person8")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )
        val rp9 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARTNER,
            Individual(id = IndividualId("9"), names = listOf(IndividualName("person9")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )
        val rp10 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("10"), names = listOf(IndividualName("person10")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )
        val rp11 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("11"), names = listOf(IndividualName("person11")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )
        val rp12 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("12"), names = listOf(IndividualName("person12")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )
        val rp13 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("13"), names = listOf(IndividualName("person13")), events = listOf(BirthEvent(), DeathEvent(confirmed = false)))
        )

        val list = listOf(
            rp1,
            rp2,
            rp3,
            rp4,
            rp5,
            rp6,
            rp7,
            rp8,
            rp9,
            rp10,
            rp11,
            rp12,
            rp13
        )

        // When
        val actual = list.toPrintableMatrix()

        // Then
//        """
//                               person8 - person9
//                                  |         |
//                                  |         |
//            person3 - person4  person7   person10
//               |         |        |         |
//               |         |        |         |
//            person2  person5 - person6   person11
//               |                            |
//               |                            |
//            person1                      person12
//                                            |
//                                            |
//                                         person13
//        """
        val expected = PrintMatrix(
            mutableMapOf(
                0 to 0 to MultiLineEntity(
                    listOf(
                        ColoredString("person1"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                ),
                0 to 1 to verticalConnection,
                0 to 2 to verticalConnection,
                0 to 3 to MultiLineEntity(
                    listOf(
                        ColoredString("person2"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                ),
                0 to 4 to verticalConnection,
                0 to 5 to verticalConnection,
                0 to 6 to MultiLineEntity(
                    listOf(
                        ColoredString("person3"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                ),
                1 to 6 to horizontalConnection,
                2 to 6 to MultiLineEntity(
                    listOf(
                        ColoredString("person4"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                ),
                2 to 5 to verticalConnection,
                2 to 4 to verticalConnection,
                2 to 3 to MultiLineEntity(
                    listOf(
                        ColoredString("person5"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                ),
                3 to 3 to horizontalConnection,
                4 to 3 to MultiLineEntity(
                    listOf(
                        ColoredString("person6"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                ),
                4 to 4 to verticalConnection,
                4 to 5 to verticalConnection,
                4 to 6 to MultiLineEntity(
                    listOf(
                        ColoredString("person7"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                ),
                4 to 7 to verticalConnection,
                4 to 8 to verticalConnection,
                4 to 9 to MultiLineEntity(
                    listOf(
                        ColoredString("person8"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                ),
                5 to 9 to horizontalConnection,
                6 to 9 to MultiLineEntity(
                    listOf(
                        ColoredString("person9"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                ),
                6 to 8 to verticalConnection,
                6 to 7 to verticalConnection,
                6 to 6 to MultiLineEntity(
                    listOf(
                        ColoredString("person10"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                ),
                6 to 5 to verticalConnection,
                6 to 4 to verticalConnection,
                6 to 3 to MultiLineEntity(
                    listOf(
                        ColoredString("person11"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                ),
                6 to 2 to verticalConnection,
                6 to 1 to verticalConnection,
                6 to 0 to MultiLineEntity(
                    listOf(
                        ColoredString("person12"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                ),
                6 to -1 to verticalConnection,
                6 to -2 to verticalConnection,
                6 to -3 to MultiLineEntity(
                    listOf(
                        ColoredString("person13"),
                        ColoredString("Birth: unknown date (UNRELIABLE)", Color.RED),
                        ColoredString("Death: unknown date (UNRELIABLE)", Color.RED)
                    ),
                    color = Color.RED
                )
            )
        )

        assertThat(actual).isEqualTo(expected)
    }

}