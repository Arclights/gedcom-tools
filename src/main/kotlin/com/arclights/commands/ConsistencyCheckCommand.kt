package com.arclights.commands

import com.arclights.Color
import com.arclights.ColoredString
import com.arclights.Gedcom
import com.arclights.consistency.ConsistencyConfig
import com.arclights.consistency.Issue
import com.arclights.consistency.Severity
import com.arclights.consistency.runConsistencyChecks

class ConsistencyCheckCommand(
    private val config: ConsistencyConfig = ConsistencyConfig()
) : Command {

    override fun getName() = "Check for inconsistencies"

    override fun run(gedcom: Gedcom) {
        val issues = runConsistencyChecks(gedcom, config)

        val errors = issues.filter { it.severity == Severity.ERROR }
        val warnings = issues.filter { it.severity == Severity.WARNING }

        println()
        println("Consistency check found ${errors.size} error(s) and ${warnings.size} warning(s)")

        printSection("Errors", errors, Color.RED)
        printSection("Warnings", warnings, Color.YELLOW)
        println()
    }

    private fun printSection(header: String, issues: List<Issue>, color: Color) {
        if (issues.isEmpty()) return
        println()
        println(ColoredString("$header (${issues.size})", color))
        issues.groupBy { it.check }.forEach { (check, checkIssues) ->
            println("  $check (${checkIssues.size}):")
            checkIssues.forEach { println("    - ${it.message}") }
        }
    }
}
