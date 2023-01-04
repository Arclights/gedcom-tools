package com.arclights

import java.io.File

class GedcomReader {
    fun read(filedPath:String):Gedcom{
        val lines = File(filedPath)
            .readLines()
        println("File read")
        return parseGedcom(lines)
    }
}