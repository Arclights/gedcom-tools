package com.arclights

import com.arclights.commands.GradeRelationship

fun <E, I> bfs(
    startEntity: E,
    identifier: (E) -> I,
    childEntities: (E) -> List<E>,
    isFinished: (E) -> Boolean
): List<E>? {
    val exploredIds = mutableSetOf(identifier(startEntity))
    val queue = queueOf(GradeRelationship.Node(null, startEntity))
    while (queue.isEmpty().not()) {
        val v = queue.dequeue()
        if (isFinished(v.value)) {
            return v.getPath()
        }
        childEntities(v.value)
            .filterNot { identifier(it) in exploredIds }
            .forEach { w ->
                exploredIds.add(identifier(w))
                queue.enqueue(GradeRelationship.Node(v, w))
            }
    }
    return null
}

private fun <T> queueOf(vararg items: T) = mutableListOf(*items)
private fun <T> MutableList<T>.dequeue(): T = removeFirst()
private fun <T> MutableList<T>.enqueue(item: T) = add(item)

/** Case-insensitive substring search over every individual's names. */
fun Gedcom.findPeopleByName(query: String): List<Individual> {
    val needle = query.lowercase()
    return individuals.values.filter { individual ->
        individual.names.any { needle in it.name.lowercase() }
    }
}