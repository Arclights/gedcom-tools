package com.arclights

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class SmokeTest {

    // Parses the bundled real-world MyHeritage export end to end as a regression guard for the
    // parser and the encoding-detection path.
    @Test
    fun parsesBundledSampleFile() {
        val bytes = File("src/main/resources/test.ged").readBytes()

        val gedcom = parseGedcom(decodeGedcomBytes(bytes))

        assertThat(gedcom.header).isNotNull
        assertThat(gedcom.individuals).isNotEmpty
        assertThat(gedcom.familyGroups).isNotEmpty
        assertThat(gedcom.sources).isNotEmpty

        // The RESI email is written with an escaped "@@"; it must come back as a single "@".
        val email = gedcom.individuals.getValue(IndividualId("@I1@"))
            .attributes.filterIsInstance<GeneralIndividualAttribute>()
            .firstNotNullOfOrNull { it.detail.address?.emails?.firstOrNull() }
        assertThat(email).isEqualTo("gustav.kvant@gmail.com")
    }
}
