package com.arclights.commands

import com.arclights.ChildToFamilyLink
import com.arclights.FamilyGroup
import com.arclights.FamilyGroupId
import com.arclights.Gedcom
import com.arclights.Individual
import com.arclights.IndividualId
import com.arclights.SpouseToFamilyLink

class GradeRelationship : Command {

    override fun getName() = "Grade relationship"

    override fun run(gedcom: Gedcom) {
        val person1 = gedcom.findPerson("the first person") ?: kotlin.run {
            return
        }
        val person2 = gedcom.findPerson("the second person") ?: kotlin.run {
            return
        }
        println("Grading relationship")
        val path = gedcom.findPath(person1, person2) ?: kotlin.run {
            println("Could not find relationship between ${person1.names.first().name} and ${person2.names.first().name}")
            return
        }

        println(path)
        path.forEach {
            println(it.person.names.first().name)
        }
    }

    private fun Gedcom.findPerson(personLabel: String): Individual? {
        print("Please provide the name of $personLabel: ")
        val name = readln()
        val matchingName =
            individuals.values.filter { it.names.any { individualName -> name in individualName.name.lowercase() } }

        if (matchingName.isEmpty()) {
            println("Found no person with name '$name'")
            return null
        }

        return if (matchingName.size == 1) {
            matchingName[0]
        } else {

            println("Found multiple matches for name '$name':")
            matchingName.forEachIndexed { i, individual ->
                println("$i: ${individual.names[0].name}")
            }
            println("Enter the number of the one you want to choose:")
            val chosenIndex = readln().toInt()

            matchingName[chosenIndex]
        }
            .also { println("Found ${it.names[0].name}") }

    }

    private fun Gedcom.findPath(from: Individual, to: Individual): List<RelationshipPart>? {
        val exploredIds = mutableSetOf(from.id)
        val queue = queueOf(Node(null, RelationshipPart(RoleInRelationship.UNKNOWN,from)))
        while (queue.isEmpty().not()) {
            val v = queue.dequeue()
            println("at person ${v.value.person.names.first().name}")
            if (v.value.person == to) {
                println("is target, returning")
                return v.getPath()
            }
            getAllFamilyRelatives(v.value.person)
                .also { println("direct relatives: $it") }
                .filterNot { it.person.id in exploredIds }
                .forEach { w ->
                    println("queueing to explore: ${w.person.names.first().name}")
                    exploredIds.add(w.person.id)
                    queue.enqueue(Node(v, w))
                }
        }
        return null
    }

    private fun Gedcom.getAllFamilyRelatives(person: Individual): List<RelationshipPart> {
        println("is child in families: ${person.childToFamilies.map { it.familyId }.map(FamilyGroupId::value)}")
        val parents = person.childToFamilies
            .map(ChildToFamilyLink::familyId)
            .map(familyGroups::getValue)
            .flatMap { listOf(it.husbandId, it.wifeId) }
            .filterNotNull()
            .map(individuals::getValue)
            .map { RelationshipPart(RoleInRelationship.PARENT, it) }

        println(
            "is spouse in families: ${
                person.spouseToFamilies.map(SpouseToFamilyLink::familyId).map(FamilyGroupId::value)
            }"
        )
        val children = person.spouseToFamilies
            .map(SpouseToFamilyLink::familyId)
            .map(familyGroups::getValue)
            .flatMap(FamilyGroup::childrenIds)
            .map(individuals::getValue)
            .map { RelationshipPart(RoleInRelationship.CHILD, it) }

        val partner = person.spouseToFamilies
            .map(SpouseToFamilyLink::familyId)
            .map(familyGroups::getValue)
            .flatMap { listOf(it.husbandId, it.wifeId) }
            .filterNotNull()
            .filterNot(person.id::equals)
            .map(individuals::getValue)
            .map { RelationshipPart(RoleInRelationship.PARTNER, it) }

        return parents + children + partner
    }

    data class RelationshipPart(val roleInRelationship: RoleInRelationship, val person: Individual)

    private data class Node<T>(val parent: Node<T>?, val value: T) {
        fun getPath(): List<T> = (parent?.getPath() ?: listOf()) + value
    }

    private fun <T> queueOf(vararg items: T) = mutableListOf(*items)
    private fun <T> MutableList<T>.dequeue(): T = removeFirst()
    private fun <T> MutableList<T>.enqueue(item: T) = add(item)

    enum class RoleInRelationship {
        PARENT,
        CHILD,
        PARTNER,
        UNKNOWN
    }
}