package io.github.riej.lsl.syntax

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import io.github.riej.lsl.parser.LslLexerAdapter
import io.github.riej.lsl.parser.LslTypes

class LslSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = LslLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            LslTypes.IDENTIFIER -> arrayOf(IDENTIFIER)
            LslTypes.INTEGER_CONSTANT, LslTypes.FLOATING_CONSTANT -> arrayOf(NUMBER)
            LslTypes.DEFAULT, LslTypes.STATE, LslTypes.JUMP, LslTypes.RETURN, LslTypes.IF, LslTypes.ELSE, LslTypes.FOR, LslTypes.DO, LslTypes.WHILE, LslTypes.PRINT -> arrayOf(
                KEYWORD
            )

            LslTypes.STRING_CONSTANT, LslTypes.UNCLOSED_STRING_CONSTANT -> arrayOf(STRING)
            LslTypes.BLOCK_COMMENT -> arrayOf(BLOCK_COMMENT)
            LslTypes.LINE_COMMENT, LslTypes.PREPROCESSOR_DIRECTIVE -> arrayOf(LINE_COMMENT)

            LslTypes.ASSIGN, LslTypes.PLUS_ASSIGN, LslTypes.MINUS_ASSIGN, LslTypes.MULTIPLE_ASSIGN, LslTypes.DIVIDE_ASSIGN, LslTypes.MODULUS_ASSIGN, LslTypes.PLUS, LslTypes.MINUS, LslTypes.MULTIPLE, LslTypes.DIVIDE, LslTypes.MODULUS, LslTypes.PLUS_PLUS, LslTypes.MINUS_MINUS, LslTypes.EQUAL, LslTypes.NOT_EQUAL, LslTypes.LESS, LslTypes.LESS_EQUAL, LslTypes.GREATER, LslTypes.GREATER_EQUAL, LslTypes.BITWISE_OR, LslTypes.BITWISE_XOR, LslTypes.BITWISE_AND, LslTypes.BITWISE_NOT, LslTypes.BOOLEAN_NOT, LslTypes.BOOLEAN_AND, LslTypes.BOOLEAN_OR, LslTypes.SHIFT_LEFT, LslTypes.SHIFT_RIGHT -> arrayOf(
                OPERATION_SIGN
            )

            LslTypes.BRACE_LEFT, LslTypes.BRACE_RIGHT -> arrayOf(BRACES)
            LslTypes.DOT -> arrayOf(DOT)
            LslTypes.SEMICOLON -> arrayOf(SEMICOLON)
            LslTypes.COMMA -> arrayOf(COMMA)
            LslTypes.PARENTHESES_LEFT, LslTypes.PARENTHESES_RIGHT -> arrayOf(PARENTHESES)
            LslTypes.BRACKET_LEFT, LslTypes.BRACKET_RIGHT -> arrayOf(BRACKETS)
            LslTypes.LABEL -> arrayOf(LABEL)
            //LslTypes.TRUE, LslTypes.FALSE, LslTypes.ZERO_VECTOR, LslTypes.ZERO_ROTATION, LslTypes.NULL_KEY -> arrayOf(CONSTANT)
            LslTypes.TYPE_NAME -> arrayOf(
                TYPENAME
            )

            else -> EMPTY_KEYS
        }
    }

    companion object {
        val IDENTIFIER = LslColorKeys.IDENTIFIER
        val NUMBER = LslColorKeys.NUMBER
        val KEYWORD = LslColorKeys.KEYWORD
        val STRING = LslColorKeys.STRING
        val BLOCK_COMMENT = LslColorKeys.BLOCK_COMMENT
        val LINE_COMMENT = LslColorKeys.LINE_COMMENT

        val OPERATION_SIGN = LslColorKeys.OPERATION_SIGN
        val BRACES = LslColorKeys.BRACES
        val DOT = LslColorKeys.DOT
        val SEMICOLON = LslColorKeys.SEMICOLON
        val COMMA = LslColorKeys.COMMA
        val PARENTHESES = LslColorKeys.PARENTHESES
        val BRACKETS = LslColorKeys.BRACKETS
        val LABEL = LslColorKeys.LABEL
        val CONSTANT = LslColorKeys.CONSTANT
        val TYPENAME = LslColorKeys.TYPE

        val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }
}