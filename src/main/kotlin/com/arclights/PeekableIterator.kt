package com.arclights

fun <T> List<T>.peekableIterator() = PeekableIterator(this)
open class PeekableIterator<T>(private val list: List<T>) : Iterator<T> {
    var i = 0;
    override fun hasNext() = i + 1 < list.size

    override fun next() = list[++i]

    fun peek() = list[i + 1]

    fun current() = list[i]
}