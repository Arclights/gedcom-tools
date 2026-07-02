package com.arclights

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class GedcomReaderKtTest {

    @Test
    fun decodesUsingTheCharsetDeclaredInTheCharTag() {
        // Given
        val content = "0 HEAD\n1 CHAR ANSI\n0 @I1@ INDI\n1 NAME Åsa /Öberg/\n0 TRLR"
        val bytes = content.toByteArray(Charset.forName("windows-1252"))

        // When
        val lines = decodeGedcomBytes(bytes)

        // Then
        assertThat(lines).contains("1 NAME Åsa /Öberg/")
    }
}
