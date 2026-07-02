package com.arclights

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset

private val logger = LoggerFactory.getLogger("GedcomReader")

class GedcomReader {
    fun read(filedPath: String): Gedcom {
        val lines = File(filedPath).readBytes().let(::decodeGedcomBytes)
        logger.debug("File read")
        return parseGedcom(lines)
    }
}

fun decodeGedcomBytes(bytes: ByteArray): List<String> =
    String(bytes, detectGedcomCharset(bytes)).lines()

fun detectGedcomCharset(bytes: ByteArray): Charset {
    return Charsets.UTF_8
}
