package com.arclights.tui

import com.arclights.commands.LabeledRow
import com.arclights.commands.NoteRow
import com.arclights.commands.PersonCard
import com.arclights.commands.PersonSection
import com.googlecode.lanterna.SGR
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.Borders
import com.googlecode.lanterna.gui2.Component
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.GridLayout
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.Panel

// The default Lanterna window theme is dark text on a light background, so accents must be dark
// enough to read against it: a bold dark blue for headings/labels and a mid-dark grey for the
// muted subtitle. Values keep the theme's default (near-black) foreground.
private val HEADING = TextColor.ANSI.BLUE
private val MUTED = TextColor.Indexed(240)

/**
 * Builds a [PersonCard] out of native Lanterna components: a styled name header, and one bordered,
 * titled panel per section whose rows are laid out in an aligned two-column grid (or as a bulleted
 * list for note-style sections). This replaces rendering the card as a single painted text blob.
 */
fun personInfoComponent(card: PersonCard): Component {
    val root = Panel(LinearLayout(Direction.VERTICAL))

    // Underline (rather than bold) for the name: bold brightens ANSI colors on most terminals,
    // turning the dark blue into a low-contrast light blue against the theme's light background.
    root.addComponent(Label(card.name).setForegroundColor(HEADING).addStyle(SGR.UNDERLINE))
    card.subtitle?.let { root.addComponent(Label(it).setForegroundColor(MUTED)) }

    card.sections.forEach { section ->
        root.addComponent(sectionPanel(section).withBorder(Borders.singleLine(section.title)))
    }

    return root
}

private fun sectionPanel(section: PersonSection): Panel {
    val labeled = section.rows.filterIsInstance<LabeledRow>()
    // Note-only sections (Notes, Also known as) read best as a bulleted list; everything else is a
    // key/value grid with the labels aligned in their own column.
    return if (labeled.isEmpty()) bulletPanel(section) else gridPanel(labeled)
}

private fun gridPanel(rows: List<LabeledRow>): Panel {
    val panel = Panel(GridLayout(2).setHorizontalSpacing(2))
    rows.forEach { row ->
        panel.addComponent(Label(row.label).setForegroundColor(HEADING))
        panel.addComponent(Label(row.value))
    }
    return panel
}

private fun bulletPanel(section: PersonSection): Panel {
    val panel = Panel(LinearLayout(Direction.VERTICAL))
    section.rows.forEach { row ->
        val text = when (row) {
            is NoteRow -> row.text
            is LabeledRow -> "${row.label}: ${row.value}"
        }
        panel.addComponent(Label("• $text"))
    }
    return panel
}
