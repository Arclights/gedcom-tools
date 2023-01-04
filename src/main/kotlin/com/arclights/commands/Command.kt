package com.arclights.commands

import com.arclights.Gedcom

interface Command {
    fun getName(): String
    fun run(gedcom: Gedcom)
}

val commands = listOf(
    GradeRelationship()
)