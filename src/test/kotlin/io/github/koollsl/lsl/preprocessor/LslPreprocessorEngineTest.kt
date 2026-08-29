package io.github.koollsl.lsl.preprocessor

import org.junit.Assert.*
import org.junit.Test

class LslPreprocessorEngineTest {

    @Test
    fun testDefinitionsTracking() {
        val defs = mutableMapOf<String, String>()

        LslPreprocessorEngine.parseAndAddDefine("PROD=\"PACK\"", defs)
        assertEquals("\"PACK\"", defs["PROD"])

        LslPreprocessorEngine.parseAndAddDefine("DEBUG 1", defs)
        assertEquals("1", defs["DEBUG"])

        LslPreprocessorEngine.parseAndAddDefine("FLAG", defs)
        assertEquals("1", defs["FLAG"])

        LslPreprocessorEngine.parseAndAddDefine("MODE=RELEASE", defs)
        assertEquals("RELEASE", defs["MODE"])

        LslPreprocessorEngine.parseAndAddDefine("#define C_STYLE_FLAG", defs)
        assertEquals("1", defs["C_STYLE_FLAG"])

        LslPreprocessorEngine.parseAndAddDefine("#define C_STYLE_VAL=200", defs)
        assertEquals("200", defs["C_STYLE_VAL"])
    }

    @Test
    fun testConditionPrefixes() {
        val defs = mapOf("PROD" to "\"PACK\"", "DEV" to "0", "ENABLED" to "1")

        assertTrue(LslPreprocessorEngine.evaluateCondition("#if PROD == \"PACK\"", defs))
        assertFalse(LslPreprocessorEngine.evaluateCondition("#if PROD == \"DEV\"", defs))

        assertTrue(LslPreprocessorEngine.evaluateCondition("#ifdef ENABLED", defs))
        assertFalse(LslPreprocessorEngine.evaluateCondition("#ifdef MISSING", defs))

        assertTrue(LslPreprocessorEngine.evaluateCondition("#ifndef MISSING", defs))
        assertFalse(LslPreprocessorEngine.evaluateCondition("#ifndef ENABLED", defs))

        assertTrue(LslPreprocessorEngine.evaluateCondition("#elif ENABLED", defs))
    }

    @Test
    fun testStringEquality() {
        val defs = mapOf("PROD" to "\"PACK\"", "DEV" to "0")

        assertTrue(LslPreprocessorEngine.evaluateCondition("PROD=\"PACK\"", defs))
        assertTrue(LslPreprocessorEngine.evaluateCondition("PROD==\"PACK\"", defs))
        assertTrue(LslPreprocessorEngine.evaluateCondition("PROD=PACK", defs))
        assertTrue(LslPreprocessorEngine.evaluateCondition("PROD==PACK", defs))
        assertFalse(LslPreprocessorEngine.evaluateCondition("PROD=\"DEV\"", defs))
        assertTrue(LslPreprocessorEngine.evaluateCondition("PROD!=\"DEV\"", defs))
    }

    @Test
    fun testLogicalOrConditions() {
        val defs = mapOf("PROD" to "\"PACK\"")

        // A | B
        assertTrue(LslPreprocessorEngine.evaluateCondition("PROD=\"DEV\" | PROD=\"PACK\"", defs))
        assertTrue(LslPreprocessorEngine.evaluateCondition("PROD=\"DEV\" || PROD=\"PACK\"", defs))
        assertFalse(LslPreprocessorEngine.evaluateCondition("PROD=\"DEV\" || PROD=\"STAGING\"", defs))

        val defs2 = mapOf("B" to "1")
        assertTrue(LslPreprocessorEngine.evaluateCondition("A | B", defs2))
        assertTrue(LslPreprocessorEngine.evaluateCondition("A || B", defs2))
        assertFalse(LslPreprocessorEngine.evaluateCondition("A || C", defs2))
    }

    @Test
    fun testLogicalAndConditions() {
        val defs = mapOf("A" to "1", "B" to "1")
        assertTrue(LslPreprocessorEngine.evaluateCondition("A & B", defs))
        assertTrue(LslPreprocessorEngine.evaluateCondition("A && B", defs))

        val defs2 = mapOf("A" to "1")
        assertFalse(LslPreprocessorEngine.evaluateCondition("A && B", defs2))
    }

    @Test
    fun testNegationAndDefined() {
        val defs = mapOf("PROD" to "\"PACK\"")
        assertTrue(LslPreprocessorEngine.evaluateCondition("defined(PROD)", defs))
        assertTrue(LslPreprocessorEngine.evaluateCondition("defined PROD", defs))
        assertFalse(LslPreprocessorEngine.evaluateCondition("!defined(PROD)", defs))
        assertTrue(LslPreprocessorEngine.evaluateCondition("!defined(DEV)", defs))
        assertTrue(LslPreprocessorEngine.evaluateCondition("!DEV", defs))
        assertFalse(LslPreprocessorEngine.evaluateCondition("!PROD", defs))
    }

    @Test
    fun testUndefTracking() {
        val defs = mutableMapOf("FOO" to "1", "BAR" to "2")

        LslPreprocessorEngine.parseAndAddDefine("#define BAZ 3", defs)
        assertEquals("3", defs["BAZ"])

        defs.remove("FOO")
        assertFalse(LslPreprocessorEngine.evaluateCondition("defined(FOO)", defs))
        assertTrue(LslPreprocessorEngine.evaluateCondition("defined(BAR)", defs))
    }

    @Test
    fun testNestedAndElifConditions() {
        val defs = mapOf("PROD" to "\"PACK\"", "FEATURE" to "1")
        assertTrue(LslPreprocessorEngine.evaluateCondition("PROD=\"PACK\" && FEATURE", defs))
        assertTrue(LslPreprocessorEngine.evaluateCondition("(PROD=\"DEV\" || PROD=\"PACK\") && (FEATURE || !defined(X))", defs))
    }

    @Test
    fun testSafeFallbacksOnNullOrMalformed() {
        assertFalse(LslPreprocessorEngine.isElementDisabled(null))
        assertEquals(emptyList<com.intellij.openapi.util.TextRange>(), LslPreprocessorEngine.getDisabledRanges(null))
        assertEquals(emptyList<com.intellij.openapi.util.TextRange>(), LslPreprocessorEngine.computeDisabledRanges(null))
        assertEquals(emptySet<com.intellij.psi.PsiFile>(), LslPreprocessorEngine.getIncludedFiles(null))

        // Malformed conditions should safely evaluate to false without throwing
        val defs = emptyMap<String, String>()
        assertFalse(LslPreprocessorEngine.evaluateCondition("", defs))
        assertFalse(LslPreprocessorEngine.evaluateCondition("   ", defs))
        assertFalse(LslPreprocessorEngine.evaluateCondition("(((", defs))
        assertFalse(LslPreprocessorEngine.evaluateCondition("! && ||", defs))
        assertFalse(LslPreprocessorEngine.evaluateCondition("defined()", defs))
        assertFalse(LslPreprocessorEngine.evaluateCondition("#ifdef", defs))
    }

    @Test
    fun testPurgeNonLslFiles() {
        val tempDir = java.nio.file.Files.createTempDirectory("build_test").toFile()
        try {
            val lslFile = java.io.File(tempDir, "script.lsl").apply { writeText("default {}") }
            val lslpFile = java.io.File(tempDir, "source.lslp").apply { writeText("#define FOO") }
            val lslmFile = java.io.File(tempDir, "header.lslm").apply { writeText("#define BAR") }

            assertTrue(lslFile.exists())
            assertTrue(lslpFile.exists())
            assertTrue(lslmFile.exists())

            LslPreprocessorEngine.purgeNonLslFiles(tempDir)

            assertTrue(lslFile.exists())
            assertFalse(lslpFile.exists())
            assertFalse(lslmFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
