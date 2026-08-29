package io.github.koollsl.lsl.parser

import org.junit.Assert.*
import org.junit.Test

class LslLexerAdapterTest {

    @Test
    fun testPreprocessorDirectiveTokenized() {
        val adapter = LslLexerAdapter()
        val code = "#include \"test.lsl\"\ndefault {\n    state_entry() {\n    }\n}\n"
        adapter.start(code, 0, code.length, 0)

        // First token MUST cover the preprocessor directive starting at offset 0
        assertEquals(0, adapter.tokenStart)
        assertEquals(19, adapter.tokenEnd)
        assertEquals(LslTypes.PREPROCESSOR_DIRECTIVE, adapter.tokenType)

        var currentOffset = adapter.tokenStart
        while (adapter.tokenType != null) {
            assertEquals("Lexer tokenStart must match previous tokenEnd", currentOffset, adapter.tokenStart)
            assertTrue("Token end must be greater than token start", adapter.tokenEnd > adapter.tokenStart)
            currentOffset = adapter.tokenEnd
            adapter.advance()
        }

        assertEquals("Lexer must cover the full buffer length", code.length, currentOffset)
    }

    @Test
    fun testMultipleDirectivesContinuousTokens() {
        val adapter = LslLexerAdapter()
        val code = "#define FOO 1\n#ifdef FOO\ninteger a = 1;\n#endif\n"
        adapter.start(code, 0, code.length, 0)

        var currentOffset = 0
        while (adapter.tokenType != null) {
            assertEquals("Tokens must be contiguous with no skipped ranges", currentOffset, adapter.tokenStart)
            assertTrue(adapter.tokenEnd > adapter.tokenStart)
            currentOffset = adapter.tokenEnd
            adapter.advance()
        }
        assertEquals(code.length, currentOffset)
    }

    @Test
    fun testEmptyAndDirectiveOnlyCode() {
        val adapter = LslLexerAdapter()
        val code = "#\n"
        adapter.start(code, 0, code.length, 0)

        assertEquals(0, adapter.tokenStart)
        assertEquals(1, adapter.tokenEnd)
        assertEquals(LslTypes.PREPROCESSOR_DIRECTIVE, adapter.tokenType)
    }
}
