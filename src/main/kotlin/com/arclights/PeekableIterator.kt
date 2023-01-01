package com.arclights

fun <T> List<T>.peekableIterator() = PeekableIterator(this)
class PeekableIterator<T>(private val list: List<T>) : Iterator<T> {
    var i = 0;
    override fun hasNext() = i < list.size

    override fun next() = list[i++]

    fun peek() = list[i]
}