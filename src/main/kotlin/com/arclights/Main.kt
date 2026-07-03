package com.arclights

import com.arclights.tui.Tui

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("No GEDCOM file supplied")
        return
    }

    val gedcom = GedcomReader().read(args[0])

    Tui().use { tui -> tui.mainMenu(gedcom) }
}
