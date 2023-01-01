package com.arclights

import java.io.File

fun main(args: Array<String>) {
    val lines = File("src/main/resources", "test.ged")
        .readLines()
    println("File read")
    parseGedcom(lines)
}