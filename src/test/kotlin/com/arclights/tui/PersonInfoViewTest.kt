package com.arclights.tui

import com.arclights.commands.LabeledRow
import com.arclights.commands.NoteRow
import com.arclights.commands.PersonCard
import com.arclights.commands.PersonSection
import com.googlecode.lanterna.gui2.Border
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.Panel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PersonInfoViewTest {

    @Test
    fun buildsHeaderLabelsAndOneBorderedPanelPerSection() {
        val card = PersonCard(
            name = "John Doe",
            subtitle = "♂ Male · b. 1947",
            sections = listOf(
                PersonSection("Events", listOf(LabeledRow("Birth", "1947"))),
                PersonSection("Notes", listOf(NoteRow("A note")))
            )
        )

        val root = personInfoComponent(card) as Panel
        val children = root.childrenList

        // name label + subtitle label + one bordered panel per section
        assertThat(children).hasSize(4)
        assertThat((children[0] as Label).text).isEqualTo("John Doe")
        assertThat((children[1] as Label).text).isEqualTo("♂ Male · b. 1947")
        assertThat(children[2]).isInstanceOf(Border::class.java)
        assertThat(children[3]).isInstanceOf(Border::class.java)
    }

    @Test
    fun omitsSubtitleLabelWhenAbsent() {
        val card = PersonCard(
            name = "Jane Roe",
            subtitle = null,
            sections = listOf(PersonSection("Family", listOf(LabeledRow("Spouse", "John Doe"))))
        )

        val root = personInfoComponent(card) as Panel

        assertThat(root.childrenList).hasSize(2) // name label + one section, no subtitle
        assertThat((root.childrenList[0] as Label).text).isEqualTo("Jane Roe")
    }
}
