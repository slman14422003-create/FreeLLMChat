package com.freellm.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StructureGuardTest {
    private fun kotlinFiles(): List<File> =
        File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun noFullLineComments() {
        val found = ArrayList<String>()
        for (f in kotlinFiles()) {
            var raw = false
            var block = false
            f.readLines().forEachIndexed { i, line ->
                val where = f.path + ":" + (i + 1)
                val toggles = line.split("\"\"\"").size % 2 == 0
                if (raw) {
                    if (toggles) raw = false
                    return@forEachIndexed
                }
                val t = line.trim()
                if (block) {
                    found.add(where)
                    if (t.contains("*/")) block = false
                    return@forEachIndexed
                }
                if (t.startsWith("//")) {
                    found.add(where)
                } else if (t.startsWith("/*")) {
                    found.add(where)
                    if (!t.contains("*/")) block = true
                }
                if (toggles) raw = true
            }
        }
        assertTrue(found.toString(), found.isEmpty())
    }

    @Test
    fun stringsParity() {
        val re = Regex("<string name=\"([^\"]+)\"")
        val ar = re.findAll(File("src/main/res/values/strings.xml").readText()).map { it.groupValues[1] }.toList()
        val en = re.findAll(File("src/main/res/values-en/strings.xml").readText()).map { it.groupValues[1] }.toList()
        assertEquals("duplicates in values: " + ar.groupBy { it }.filter { it.value.size > 1 }.keys, ar.size, ar.toSet().size)
        assertEquals("duplicates in values-en: " + en.groupBy { it }.filter { it.value.size > 1 }.keys, en.size, en.toSet().size)
        val diff = (ar.toSet() - en.toSet()) + (en.toSet() - ar.toSet())
        assertTrue(diff.toString(), diff.isEmpty())
    }

    @Test
    fun fileSizeCeiling() {
        val offenders = kotlinFiles().filter { it.readLines().size > 1600 }.map { it.path + ":" + it.readLines().size }
        assertTrue(offenders.toString(), offenders.isEmpty())
    }
}
