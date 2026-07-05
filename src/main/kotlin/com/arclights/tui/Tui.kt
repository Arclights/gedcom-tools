package com.arclights.tui

import com.arclights.Gedcom
import com.arclights.Individual
import com.arclights.PrintMatrix
import com.arclights.commands.commands
import com.arclights.findPeopleByName
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.Button
import com.googlecode.lanterna.gui2.DefaultWindowManager
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.EmptySpace
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.Window
import com.googlecode.lanterna.gui2.dialogs.MessageDialog
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton
import com.googlecode.lanterna.gui2.dialogs.ListSelectDialogBuilder
import com.googlecode.lanterna.gui2.dialogs.TextInputDialog
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory

/**
 * Full-screen, keyboard-driven front end built on Lanterna. Owns the terminal screen and the window
 * manager, and exposes the interactions the commands need (pick a person, show a report, show a
 * message). Use it with [use] so the terminal is always restored on exit.
 */
class Tui : AutoCloseable {
    private val screen = DefaultTerminalFactory().createScreen().apply { startScreen() }
    private val gui = MultiWindowTextGUI(screen, DefaultWindowManager(), EmptySpace(TextColor.ANSI.BLUE))

    override fun close() {
        screen.stopScreen()
    }

    /** Shows the main menu and blocks until the user quits. */
    fun mainMenu(gedcom: Gedcom) {
        val window = BasicWindow("GEDCOM Tools")
        window.setHints(listOf(Window.Hint.CENTERED))

        val actions = ActionListBox()
        commands.forEach { command ->
            actions.addItem(command.getName()) { command.run(gedcom, this) }
        }
        actions.addItem("Quit") { window.close() }

        window.component = Panel(LinearLayout(Direction.VERTICAL)).apply {
            addComponent(Label("Select a command (↑/↓, Enter):"))
            addComponent(actions)
        }
        gui.addWindowAndWait(window)
    }

    /**
     * Prompts for a name and resolves it to a single [Individual]: cancelling returns null, no match
     * shows a message and returns null, several matches are disambiguated with a selection dialog.
     */
    fun selectPerson(gedcom: Gedcom, personLabel: String): Individual? {
        val query = TextInputDialog.showDialog(gui, "Find person", "Enter the name of $personLabel:", "")
            ?: return null

        val matches = gedcom.findPeopleByName(query)
        if (matches.isEmpty()) {
            messageBox("No match", "Found no person with name '$query'")
            return null
        }
        if (matches.size == 1) return matches.single()

        val builder = ListSelectDialogBuilder<PersonChoice>()
            .setTitle("Multiple matches")
            .setDescription("Found ${matches.size} people matching '$query'. Select one:")
        matches.forEach { builder.addListItem(PersonChoice(it)) }
        return builder.build().showDialog(gui)?.individual
    }

    /** Shows a relationship [matrix], drawn with native TUI components, in a scrollable window. */
    fun showMatrix(title: String, matrix: PrintMatrix) {
        val window = BasicWindow(title)
        window.setHints(listOf(Window.Hint.CENTERED, Window.Hint.EXPANDED))

        val view = RelationshipMatrixView(matrix) { window.close() }
            .apply { layoutData = LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow) }

        window.component = Panel(LinearLayout(Direction.VERTICAL)).apply {
            addComponent(view)
            addComponent(Label("(arrows/PgUp/PgDn to scroll, Tab to reach Close, Esc to close)"))
            addComponent(Button("Close") { window.close() })
        }
        gui.addWindowAndWait(window)
    }

    /** Shows [content] (which may contain color escapes) in a scrollable, dismissable window. */
    fun showText(title: String, content: String) {
        val window = BasicWindow(title)
        window.setHints(listOf(Window.Hint.CENTERED, Window.Hint.EXPANDED))

        val view = AnsiTextView(content) { window.close() }
            .apply { layoutData = LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow) }

        window.component = Panel(LinearLayout(Direction.VERTICAL)).apply {
            addComponent(view)
            addComponent(Label("(↑/↓/PgUp/PgDn to scroll, Tab to reach Close, Esc to close)"))
            addComponent(Button("Close") { window.close() })
        }
        gui.addWindowAndWait(window)
    }

    fun messageBox(title: String, text: String) {
        MessageDialog.showMessageDialog(gui, title, text, MessageDialogButton.OK)
    }
}

/** Wraps an [Individual] so the selection dialog lists it by name rather than its data-class form. */
private class PersonChoice(val individual: Individual) {
    override fun toString() = individual.names.firstOrNull()?.name ?: individual.id.value
}
