package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeFilesTest {
    private val fence = "```"

    @Test fun fileLineBeforeFenceNamesTheFile() {
        val md = "Here:\n\nFile: app/build.gradle.kts\n${fence}kotlin\nplugins {}\n$fence\n"
        val f = CodeFiles.extract(md)
        assertEquals(listOf(CodeFile("app/build.gradle.kts", "plugins {}\n")), f)
    }

    @Test fun headingAndBoldAndBacktickLabelsWork() {
        val md = "### File: a/One.kt\n${fence}kotlin\n1\n$fence\n**a/Two.kt**\n$fence\n2\n$fence\n`a/Three.kt`\n$fence\n3\n$fence\n1. `a/Four.kt`\n$fence\n4\n$fence\n"
        assertEquals(listOf("a/One.kt", "a/Two.kt", "a/Three.kt", "a/Four.kt"), CodeFiles.extract(md).map { it.path })
    }

    @Test fun infoStringCarriesThePath() {
        val md = "${fence}kotlin title=\"x/Main.kt\"\nm\n$fence\n${fence}xml:res/values/a.xml\n<a/>\n$fence\n${fence}java src/B.java\nb\n$fence\n"
        assertEquals(listOf("x/Main.kt", "res/values/a.xml", "src/B.java"), CodeFiles.extract(md).map { it.path })
    }

    @Test fun firstLineCommentNamesTheFile() {
        val md = "${fence}kotlin\n// File: p/Q.kt\nclass Q\n$fence\n"
        assertEquals("p/Q.kt", CodeFiles.extract(md).single().path)
    }

    @Test fun unlabeledBlocksAreIgnored() {
        val md = "Run this:\n${fence}bash\nls -la\n$fence\n"
        assertTrue(CodeFiles.extract(md).isEmpty())
    }

    @Test fun traversalAndAbsolutePathsAreRejectedOrNormalized() {
        assertNull(CodeFiles.safePath("../etc/passwd"))
        assertNull(CodeFiles.safePath("a/../../b.txt"))
        assertEquals("a/b.txt", CodeFiles.safePath("/a/b.txt"))
        assertEquals("a/b.txt", CodeFiles.safePath("./a\\b.txt"))
        assertNull(CodeFiles.safePath("note"))
        assertEquals("Dockerfile", CodeFiles.safePath("Dockerfile"))
    }

    @Test fun laterDuplicateOverridesEarlier() {
        val md = "File: a.txt\n$fence\none\n$fence\nFile: a.txt\n$fence\ntwo\n$fence\n"
        val f = CodeFiles.extract(md)
        assertEquals(1, f.size)
        assertEquals("two\n", f[0].content)
    }

    @Test fun closingFenceOfPreviousBlockIsNotALabel() {
        val md = "File: a.txt\n$fence\none\n$fence\n$fence\ntwo\n$fence\n"
        assertEquals(listOf("a.txt"), CodeFiles.extract(md).map { it.path })
    }

    @Test fun projectNameUsesSharedRootOrFallback() {
        assertEquals("MyApp", CodeFiles.projectName(listOf(CodeFile("MyApp/a.kt", ""), CodeFile("MyApp/b/c.kt", ""))))
        assertEquals("project", CodeFiles.projectName(listOf(CodeFile("a.kt", ""), CodeFile("b/c.kt", ""))))
    }

    @Test fun projectRequestsAreDetected() {
        assertTrue(CodeFiles.wantsProject("اكتب لي مشروع أندرويد كامل"))
        assertTrue(CodeFiles.wantsProject("create a todo app for android"))
        assertTrue(CodeFiles.wantsProject("اعمل تطبيق حاسبة"))
        assertFalse(CodeFiles.wantsProject("اكتب لي دالة لجمع رقمين"))
        assertFalse(CodeFiles.wantsProject("what is an app"))
    }
}
