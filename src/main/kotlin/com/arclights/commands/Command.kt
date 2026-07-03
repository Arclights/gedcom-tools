package com.arclights.commands

import com.arclights.Gedcom
import com.arclights.tui.Tui

interface Command {
    fun getName(): String
    fun run(gedcom: Gedcom, tui: Tui)
}

val commands = listOf(
    GradeRelationship(),
    PersonInfo(),
    ConsistencyCheckCommand()
)