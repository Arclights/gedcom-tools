package com.arclights

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class PrintMatrixKtTest {
    @ParameterizedTest
    @MethodSource
    fun padsStringToCorrectWidth(input: String, padding: Int, expected: String) {
        // When
        val actual = input.padTo(padding)

        // Then
        assertThat(actual).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        fun padsStringToCorrectWidth(): Stream<Arguments> = Stream.of(
            Arguments.of("", 7, "       "),
            Arguments.of("|", 7, "   |   "),
            Arguments.of("-", 7, "   -   "),
            Arguments.of("ll", 6, "  ll  "),
            Arguments.of("ll", 5, " ll  "),
            Arguments.of("lll", 6, " lll  ")
        )
    }
}