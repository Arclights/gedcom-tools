package com.arclights

import java.text.Normalizer

/**
 * Decodes ANSEL (ANSI/NISO Z39.47), the character set GEDCOM 5.5.1 names as its default.
 *
 * Bytes below 0x80 are plain ASCII. Bytes 0xA1–0xCF map to standalone special characters.
 * Bytes 0xE0–0xFE are combining diacritics that, unlike Unicode, precede the base letter they
 * modify; they are buffered and emitted after the base character, then the whole string is
 * normalized to NFC so common precomposed letters (å, ä, ö, é, …) come out as single code points.
 */
fun decodeAnsel(bytes: ByteArray): String {
    val out = StringBuilder(bytes.size)
    val pendingDiacritics = StringBuilder()

    for (byte in bytes) {
        val code = byte.toInt() and 0xFF
        when {
            code < 0x80 -> {
                out.append(code.toChar())
                flushDiacritics(out, pendingDiacritics)
            }

            code in ANSEL_DIACRITICS -> pendingDiacritics.append(ANSEL_DIACRITICS.getValue(code))

            else -> {
                out.append(ANSEL_SPECIALS[code] ?: code.toChar())
                flushDiacritics(out, pendingDiacritics)
            }
        }
    }
    // Any diacritic left with no following base character is emitted on its own.
    out.append(pendingDiacritics)

    return Normalizer.normalize(out, Normalizer.Form.NFC)
}

private fun flushDiacritics(out: StringBuilder, pending: StringBuilder) {
    if (pending.isNotEmpty()) {
        out.append(pending)
        pending.setLength(0)
    }
}

// Standalone graphic characters in the ANSEL upper range.
private val ANSEL_SPECIALS: Map<Int, Char> = mapOf(
    0xA1 to 'Ł', 0xA2 to 'Ø', 0xA3 to 'Đ', 0xA4 to 'Þ', 0xA5 to 'Æ', 0xA6 to 'Œ',
    0xA7 to 'ʹ', 0xA8 to '·', 0xA9 to '♭', 0xAA to '®', 0xAB to '±', 0xAC to 'Ơ',
    0xAD to 'Ư', 0xAE to 'ʼ',
    0xB0 to 'ʻ', 0xB1 to 'ł', 0xB2 to 'ø', 0xB3 to 'đ', 0xB4 to 'þ', 0xB5 to 'æ',
    0xB6 to 'œ', 0xB7 to 'ʺ', 0xB8 to 'ı', 0xB9 to '£', 0xBA to 'ð',
    0xC0 to '°', 0xC1 to 'ℓ', 0xC2 to '℗', 0xC3 to '©', 0xC4 to '♯', 0xC5 to '¿',
    0xC6 to '¡', 0xC7 to 'ß'
)

// Combining diacritics, mapped to their Unicode combining code points.
private val ANSEL_DIACRITICS: Map<Int, Char> = mapOf(
    0xE0 to '̉', // hook above
    0xE1 to '̀', // grave
    0xE2 to '́', // acute
    0xE3 to '̂', // circumflex
    0xE4 to '̃', // tilde
    0xE5 to '̄', // macron
    0xE6 to '̆', // breve
    0xE7 to '̇', // dot above
    0xE8 to '̈', // diaeresis / umlaut
    0xE9 to '̌', // caron / hacek
    0xEA to '̊', // ring above
    0xEB to '︠', // ligature left half
    0xEC to '︡', // ligature right half
    0xED to '̕', // comma above right
    0xEE to '̋', // double acute
    0xEF to '̐', // candrabindu
    0xF0 to '̧', // cedilla
    0xF1 to '̨', // ogonek
    0xF2 to '̣', // dot below
    0xF3 to '̤', // double dot below
    0xF4 to '̥', // ring below
    0xF5 to '̳', // double low line
    0xF6 to '̲', // low line
    0xF7 to '̦', // comma below
    0xF8 to '̜', // left half ring below
    0xF9 to '̮', // breve below
    0xFA to '︢', // double tilde left half
    0xFB to '︣', // double tilde right half
    0xFE to '̓' // comma above
)
