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

fun decodeGedcomBytes(bytes: ByteArray): List<String> {
    // ANSEL is GEDCOM's own default character set and has no equivalent JVM Charset, so it
    // needs a dedicated decoder rather than the Charset path below.
    if (!hasUtf16Bom(bytes) && declaredEncoding(bytes)?.uppercase() == "ANSEL") {
        return decodeAnsel(bytes).lines()
    }
    return String(bytes, detectGedcomCharset(bytes)).lines()
}

private fun hasUtf16Bom(bytes: ByteArray): Boolean =
    bytes.size >= 2 &&
        ((bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) ||
            (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()))

// GEDCOM's own encoding is declared inside the file, so this first pass has to use a charset
// that can decode any byte sequence without throwing. ISO-8859-1 maps every byte to a
// character 1:1, and the CHAR tag's value is always plain ASCII, so this is safe regardless
// of the file's real encoding.
private fun declaredEncoding(bytes: ByteArray): String? =
    String(bytes, Charsets.ISO_8859_1)
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it == "1 CHAR" || it.startsWith("1 CHAR ") }
        ?.removePrefix("1 CHAR")
        ?.trim()

fun detectGedcomCharset(bytes: ByteArray): Charset {
    // A UTF-16 byte-order mark has to be detected before the declared-encoding scan below:
    // in a UTF-16 file every other byte is a null, so "1 CHAR UNICODE" never matches as a
    // plain string and the declared encoding would be missed. The UTF_16 charset uses the
    // BOM to pick the byte order.
    if (hasUtf16Bom(bytes)) {
        return Charsets.UTF_16
    }

    val declared = declaredEncoding(bytes)
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
