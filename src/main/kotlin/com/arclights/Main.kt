package com.arclights

import com.arclights.commands.commands

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("No GEDCOM file supplied")
    }

    val gedcom = GedcomReader().read(args[0])

    while (true) {
        println("Select one of the commands")
        commands.forEachIndexed { i, command ->
            println("$i: ${command.getName()}")
        }
        val command = readln()
        commands[command.toInt()].run(gedcom)
    }
}