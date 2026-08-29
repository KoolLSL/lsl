package io.github.koollsl.lsl.syntax

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

object LslColorKeys {
    val IDENTIFIER =
        TextAttributesKey.createTextAttributesKey("LSL_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
    val NUMBER =
        TextAttributesKey.createTextAttributesKey("LSL_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val KEYWORD =
        TextAttributesKey.createTextAttributesKey("LSL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val STRING =
        TextAttributesKey.createTextAttributesKey("LSL_STRING", DefaultLanguageHighlighterColors.STRING)
    val BLOCK_COMMENT =
        TextAttributesKey.createTextAttributesKey("LSL_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
    val LINE_COMMENT =
        TextAttributesKey.createTextAttributesKey("LSL_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)

    val OPERATION_SIGN =
        TextAttributesKey.createTextAttributesKey("LSL_OPERATION_SIGN", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val BRACES =
        TextAttributesKey.createTextAttributesKey("LSL_BRACES", DefaultLanguageHighlighterColors.BRACES)
    val DOT =
        TextAttributesKey.createTextAttributesKey("LSL_DOT", DefaultLanguageHighlighterColors.DOT)
    val SEMICOLON =
        TextAttributesKey.createTextAttributesKey("LSL_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)
    val COMMA =
        TextAttributesKey.createTextAttributesKey("LSL_COMMA", DefaultLanguageHighlighterColors.COMMA)
    val PARENTHESES =
        TextAttributesKey.createTextAttributesKey("LSL_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
    val BRACKETS =
        TextAttributesKey.createTextAttributesKey("LSL_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
    val LABEL =
        TextAttributesKey.createTextAttributesKey("LSL_LABEL", DefaultLanguageHighlighterColors.LABEL)
    val CONSTANT =
        TextAttributesKey.createTextAttributesKey("LSL_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT)

    val TYPE =
        TextAttributesKey.createTextAttributesKey("LSL_TYPE", DefaultLanguageHighlighterColors.KEYWORD)
    val TYPENAME = TYPE
    val TYPES = TYPE

    val BUILTIN_FUNCTION =
        TextAttributesKey.createTextAttributesKey("LSL_BUILTIN_FUNCTION", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL)
    val BUILTIN_FUNCTIONS = BUILTIN_FUNCTION

    val BUILTIN_CONSTANT =
        TextAttributesKey.createTextAttributesKey("LSL_BUILTIN_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT)
    val BUILTIN_CONSTANTS = BUILTIN_CONSTANT

    val EVENT =
        TextAttributesKey.createTextAttributesKey("LSL_EVENT", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
    val EVENTS = EVENT
}
