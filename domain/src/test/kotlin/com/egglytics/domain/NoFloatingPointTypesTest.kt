package com.egglytics.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoFloatingPointTypesTest {

    @Test
    fun `domain main sources do not use Float or Double (AC-36)`() {
        val sourceRoot = resolveDomainMainSourceRoot()
        assertTrue(
            "No se encontró el directorio de fuentes de :domain en ${sourceRoot.absolutePath}",
            sourceRoot.isDirectory,
        )

        val kotlinFiles = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.path }
            .toList()

        assertTrue(
            "No se encontró ningún archivo .kt en ${sourceRoot.absolutePath}",
            kotlinFiles.isNotEmpty(),
        )

        val violations = kotlinFiles.flatMap { file ->
            findFloatingPointViolations(file.path, file.readText())
        }

        assertTrue(
            "Se encontraron usos prohibidos de coma flotante (AC-36):\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `scanner detects explicit Double, toFloat, 1_5 and 2f`() {
        val sample = """
            package sample
            val a: Double = 1.5
            val b = 2f
            val c = 10.toFloat()
        """.trimIndent()

        val violations = findFloatingPointViolations("Sample.kt", sample)
        assertEquals(
            listOf(
                "Sample.kt:2: Double",
                "Sample.kt:2: 1.5",
                "Sample.kt:3: 2f",
                "Sample.kt:4: toFloat",
            ),
            violations,
        )
    }

    @Test
    fun `scanner ignores matches inside comments and strings`() {
        val sample = """
            package sample
            // val a: Double = 1.5
            /* val b = 2f; val c = 10.toFloat() */
            val text = "Double toFloat() 1.5 2f"
            val ok: Long = 1500L
        """.trimIndent()

        val violations = findFloatingPointViolations("Clean.kt", sample)
        assertTrue("No debía reportar coincidencias en comentarios o strings: $violations", violations.isEmpty())
    }

    private fun resolveDomainMainSourceRoot(): File {
        val fromModuleDir = File("src/main/kotlin")
        if (fromModuleDir.isDirectory) return fromModuleDir
        return File("domain/src/main/kotlin")
    }

    internal fun findFloatingPointViolations(filePath: String, content: String): List<String> {
        val sanitized = stripCommentsAndStringsPreservingLines(content)
        val patterns = listOf(
            Regex("""\b(?:Double|Float)\b"""),
            Regex("""\bto(?:Double|Float)\b"""),
            Regex("""\b\d+\.\d+[fF]?\b"""),
            Regex("""\b\d+[fF]\b"""),
        )

        val violations = mutableListOf<String>()
        sanitized.lines().forEachIndexed { index, line ->
            val lineNumber = index + 1
            patterns.forEach { regex ->
                regex.findAll(line).forEach { match ->
                    violations += "$filePath:$lineNumber: ${match.value}"
                }
            }
        }
        return violations
    }

    private fun stripCommentsAndStringsPreservingLines(input: String): String {
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            i = when {
                input.startsWith("//", i) -> skipLineComment(input, i)
                input.startsWith("/*", i) -> skipBlockComment(input, i, out)
                input.startsWith("\"\"\"", i) -> skipTripleQuotedString(input, i, out)
                input[i] == '"' -> {
                    out.append(' ')
                    skipQuotedString(input, i, out)
                }
                else -> {
                    out.append(input[i])
                    i + 1
                }
            }
        }
        return out.toString()
    }

    private fun skipLineComment(input: String, start: Int): Int {
        var i = start + 2
        while (i < input.length && input[i] != '\n') i++
        return i
    }

    private fun skipBlockComment(input: String, start: Int, out: StringBuilder): Int {
        val end = input.indexOf("*/", start + 2)
        val stop = if (end == -1) input.length else end + 2
        appendNewLines(input, start + 2, stop, out)
        return stop
    }

    private fun skipTripleQuotedString(input: String, start: Int, out: StringBuilder): Int {
        val end = input.indexOf("\"\"\"", start + 3)
        val stop = if (end == -1) input.length else end + 3
        appendNewLines(input, start + 3, stop, out)
        return stop
    }

    private fun skipQuotedString(input: String, start: Int, out: StringBuilder): Int {
        var i = start + 1
        while (i < input.length) {
            val ch = input[i]
            if (ch == '\\' && i + 1 < input.length) {
                i += 2
            } else if (ch == '"') {
                return i + 1
            } else {
                if (ch == '\n') out.append('\n')
                i++
            }
        }
        return i
    }

    private fun appendNewLines(input: String, from: Int, to: Int, out: StringBuilder) {
        for (idx in from until to) {
            if (input[idx] == '\n') out.append('\n')
        }
    }
}
