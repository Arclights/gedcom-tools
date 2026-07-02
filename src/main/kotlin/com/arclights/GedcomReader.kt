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
    // GEDCOM's own encoding is declared inside the file, so this first pass has to
    // use a charset that can decode any byte sequence without throwing. ISO-8859-1
    // maps every byte to a character 1:1, and the CHAR tag's value is always plain
    // ASCII, so this is safe regardless of the file's real encoding.
    val declared = String(bytes, Charsets.ISO_8859_1)
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it == "1 CHAR" || it.startsWith("1 CHAR ") }
        ?.removePrefix("1 CHAR")
        ?.trim()

    return when (declared?.uppercase()) {
        null, "" -> Charsets.UTF_8
        "UTF-8", "UTF8" -> Charsets.UTF_8
        "ASCII" -> Charsets.US_ASCII
        "ANSI" -> Charset.forName("windows-1252")
        "UNICODE" -> Charsets.UTF_16
        else -> {
            logger.warn("Unsupported declared character encoding '$declared', falling back to UTF-8")
            Charsets.UTF_8
        }
    }
}
