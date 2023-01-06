package com.arclights.commands

import com.arclights.Individual
import com.arclights.IndividualId
import com.arclights.IndividualName
import com.arclights.commands.SingleLineEntity.Companion.horizontalConnection
import com.arclights.commands.SingleLineEntity.Companion.verticalConnection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class GradeRelationshipTest {
    @ParameterizedTest
    @MethodSource
    fun padsStringToCorrectWidth(input: String, padding: Int, expected: String) {
        // When
        val actual = input.padTo(padding)

        // Then
        assertThat(actual).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        fun padsStringToCorrectWidth(): Stream<Arguments> = Stream.of(
            Arguments.of("", 7, "       "),
            Arguments.of("|", 7, "   |   "),
            Arguments.of("-", 7, "   -   "),
            Arguments.of("ll", 6, "  ll  "),
            Arguments.of("ll", 5, " ll  "),
            Arguments.of("lll", 6, " lll  ")
        )
    }

    @Test
    fun canBuildCorrectMatrix() {
        // Given
        val rp1 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.UNKNOWN,
            Individual(id = IndividualId("1"), names = listOf(IndividualName("person1")))
        )
        val rp2 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARENT,
            Individual(id = IndividualId("2"), names = listOf(IndividualName("person2")))
        )
        val rp3 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARENT,
            Individual(id = IndividualId("3"), names = listOf(IndividualName("person3")))
        )
        val rp4 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARTNER,
            Individual(id = IndividualId("4"), names = listOf(IndividualName("person4")))
        )
        val rp5 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("5"), names = listOf(IndividualName("person5")))
        )
        val rp6 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARTNER,
            Individual(id = IndividualId("6"), names = listOf(IndividualName("person6")))
        )
        val rp7 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARENT,
            Individual(id = IndividualId("7"), names = listOf(IndividualName("person7")))
        )
        val rp8 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARENT,
            Individual(id = IndividualId("8"), names = listOf(IndividualName("person8")))
        )
        val rp9 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARTNER,
            Individual(id = IndividualId("9"), names = listOf(IndividualName("person9")))
        )
        val rp10 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("10"), names = listOf(IndividualName("person10")))
        )
        val rp11 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("11"), names = listOf(IndividualName("person11")))
        )
        val rp12 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("12"), names = listOf(IndividualName("person12")))
        )
        val rp13 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("13"), names = listOf(IndividualName("person13")))
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
                0 to 0 to MultiLineEntity(listOf("person1")),
                0 to 1 to verticalConnection,
                0 to 2 to verticalConnection,
                0 to 3 to MultiLineEntity(listOf("person2")),
                0 to 4 to verticalConnection,
                0 to 5 to verticalConnection,
                0 to 6 to MultiLineEntity(listOf("person3")),
                1 to 6 to horizontalConnection,
                2 to 6 to MultiLineEntity(listOf("person4")),
                2 to 5 to verticalConnection,
                2 to 4 to verticalConnection,
                2 to 3 to MultiLineEntity(listOf("person5")),
                3 to 3 to horizontalConnection,
                4 to 3 to MultiLineEntity(listOf("person6")),
                4 to 4 to verticalConnection,
                4 to 5 to verticalConnection,
                4 to 6 to MultiLineEntity(listOf("person7")),
                4 to 7 to verticalConnection,
                4 to 8 to verticalConnection,
                4 to 9 to MultiLineEntity(listOf("person8")),
                5 to 9 to horizontalConnection,
                6 to 9 to MultiLineEntity(listOf("person9")),
                6 to 8 to verticalConnection,
                6 to 7 to verticalConnection,
                6 to 6 to MultiLineEntity(listOf("person10")),
                6 to 5 to verticalConnection,
                6 to 4 to verticalConnection,
                6 to 3 to MultiLineEntity(listOf("person11")),
                6 to 2 to verticalConnection,
                6 to 1 to verticalConnection,
                6 to 0 to MultiLineEntity(listOf("person12")),
                6 to -1 to verticalConnection,
                6 to -2 to verticalConnection,
                6 to -3 to MultiLineEntity(listOf("person13"))
            )
        )

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun canPrintRelationshipTree() {
        // Given
        val rp1 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.UNKNOWN,
            Individual(id = IndividualId("1"), names = listOf(IndividualName("person1")))
        )
        val rp2 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARENT,
            Individual(id = IndividualId("2"), names = listOf(IndividualName("person2")))
        )
        val rp3 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARENT,
            Individual(id = IndividualId("3"), names = listOf(IndividualName("person3")))
        )
        val rp5 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("5"), names = listOf(IndividualName("person5")))
        )
        val rp6 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARTNER,
            Individual(id = IndividualId("6"), names = listOf(IndividualName("person6")))
        )
        val rp7 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARENT,
            Individual(id = IndividualId("7"), names = listOf(IndividualName("person7")))
        )
        val rp8 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARENT,
            Individual(id = IndividualId("8"), names = listOf(IndividualName("person8")))
        )
        val rp9 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.PARTNER,
            Individual(id = IndividualId("9"), names = listOf(IndividualName("person9")))
        )
        val rp10 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("10"), names = listOf(IndividualName("person10")))
        )
        val rp11 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("11"), names = listOf(IndividualName("person11")))
        )
        val rp12 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("12"), names = listOf(IndividualName("person12")))
        )
        val rp13 = GradeRelationship.RelationshipPart(
            GradeRelationship.RoleInRelationship.CHILD,
            Individual(id = IndividualId("13"), names = listOf(IndividualName("person13")))
        )

        val list = listOf(
            rp1,
            rp2,
            rp3,
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
        val actual = list.toPrintableString()

        // Then
        val expected = """                        +-------+   +--------+ 
                        |person8| - |person9 | 
                        +-------+   +--------+ 
                            |           |      
                            |           |      
 +------------------+   +-------+   +--------+ 
 |     person3      |   |person7|   |person10| 
 +------------------+   +-------+   +--------+ 
     |          |           |           |      
     |          |           |           |      
 +-------+  +-------+   +-------+   +--------+ 
 |person2|  |person5| - |person6|   |person11| 
 +-------+  +-------+   +-------+   +--------+ 
     |                                  |      
     |                                  |      
 +-------+                          +--------+ 
 |person1|                          |person12| 
 +-------+                          +--------+ 
                                        |      
                                        |      
                                    +--------+ 
                                    |person13| 
                                    +--------+ """

        assertThat(actual).isEqualTo(expected)
    }

//    val familyId1 = FamilyGroupId("fam1")
//    val familyId2 = FamilyGroupId("fam2")
//    val familyId3 = FamilyGroupId("fam3")
//    val familyId4 = FamilyGroupId("fam4")
//    val familyId5 = FamilyGroupId("fam5")
//    val familyId6 = FamilyGroupId("fam6")
//    val familyId7 = FamilyGroupId("fam7")
//
//    Individual(
//    id = IndividualId("1"), names = listOf(IndividualName("person1")), childToFamilies = listOf(
//    ChildToFamilyLink(familyId1)
//    )
//    )
//    Individual(
//    id = IndividualId("2"), names = listOf(IndividualName("person2")), childToFamilies = listOf(
//    ChildToFamilyLink(familyId2)
//    )
//    )
//    Individual(
//    id = IndividualId("3"), names = listOf(IndividualName("person3")), spouseToFamilies = listOf(
//    SpouseToFamilyLink(familyId2)
//    )
//    )
//    Individual(
//    id = IndividualId("4"), names = listOf(IndividualName("person4")), spouseToFamilies = listOf(
//    SpouseToFamilyLink(familyId2)
//    )
//    )
//    Individual(
//    id = IndividualId("5"), names = listOf(IndividualName("person5")), childToFamilies = listOf(
//    ChildToFamilyLink(familyId2)
//    ), spouseToFamilies = listOf(SpouseToFamilyLink())
//    )
//    Individual(id = IndividualId("6"), names = listOf(IndividualName("person6")))
//    Individual(id = IndividualId("7"), names = listOf(IndividualName("person7")))
//    Individual(id = IndividualId("8"), names = listOf(IndividualName("person8")))
//    Individual(id = IndividualId("9"), names = listOf(IndividualName("person9")))
//    Individual(id = IndividualId("10"), names = listOf(IndividualName("person10")))
//    Individual(id = IndividualId("11"), names = listOf(IndividualName("person11")))
//    Individual(id = IndividualId("12"), names = listOf(IndividualName("person12")))
//    Individual(id = IndividualId("13"), names = listOf(IndividualName("person13")))
}