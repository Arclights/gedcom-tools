package com.arclights.commands

import com.arclights.Color
import com.arclights.ColoredString
import com.arclights.Gedcom
import com.arclights.consistency.ConsistencyConfig
import com.arclights.consistency.Issue
import com.arclights.consistency.Severity
import com.arclights.consistency.runConsistencyChecks
import com.arclights.tui.Tui

class ConsistencyCheckCommand(
    private val config: ConsistencyConfig = ConsistencyConfig()
) : Command {

    override fun getName() = "Check for inconsistencies"

    override fun run(gedcom: Gedcom, tui: Tui) {
        val issues = runConsistencyChecks(gedcom, config)

        val errors = issues.filter { it.severity == Severity.ERROR }
        val warnings = issues.filter { it.severity == Severity.WARNING }

        val report = buildString {
            appendLine("Consistency check found ${errors.size} error(s) and ${warnings.size} warning(s)")
            appendSection("Errors", errors, Color.RED)
            appendSection("Warnings", warnings, Color.YELLOW)
        }.trimEnd('\n')

        tui.showText("Consistency check", report)
    }

    private fun StringBuilder.appendSection(header: String, issues: List<Issue>, color: Color) {
        if (issues.isEmpty()) return
        appendLine()
        appendLine(ColoredString("$header (${issues.size})", color).toString())
        issues.groupBy { it.check }.forEach { (check, checkIssues) ->
            appendLine("  $check (${checkIssues.size}):")
            checkIssues.forEach { appendLine("    - ${it.message}") }
        }
    }
}
