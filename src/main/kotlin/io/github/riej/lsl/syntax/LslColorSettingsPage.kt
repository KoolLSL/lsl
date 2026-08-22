package io.github.riej.lsl.syntax

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import io.github.riej.lsl.LslIcons
import io.github.riej.lsl.LslLanguage
import javax.swing.Icon

class LslColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = LslIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = LslSyntaxHighlighter()

    override fun getDemoText(): String =
        """
        // Line comment
        /* Block
           comment */

        <type>vector</type> gPosition = <builtin_constant>ZERO_VECTOR</builtin_constant>;
        <type>integer</type> gCounter = 0;

        <keyword>default</keyword> {
            <event>state_entry</event>() {
                <builtin_function>llOwnerSay</builtin_function>("Script initialized: " + (<type>string</type>)gCounter);
            }

            <event>touch_start</event>(<type>integer</type> total_number) {
                gPosition = <builtin_function>llGetPos</builtin_function>();
                <builtin_function>llSay</builtin_function>(0, "Touched at position: " + (<type>string</type>)gPosition);
                <keyword>if</keyword> (total_number > 1) {
                    <keyword>jump</keyword> finish;
                }
        @finish;
            }
        }
        """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = mapOf(
        "keyword" to LslColorKeys.KEYWORD,
        "type" to LslColorKeys.TYPE,
        "builtin_function" to LslColorKeys.BUILTIN_FUNCTION,
        "builtin_constant" to LslColorKeys.BUILTIN_CONSTANT,
        "event" to LslColorKeys.EVENT,
        "constant" to LslColorKeys.CONSTANT,
        "identifier" to LslColorKeys.IDENTIFIER,
        "number" to LslColorKeys.NUMBER,
        "string" to LslColorKeys.STRING,
        "line_comment" to LslColorKeys.LINE_COMMENT,
        "block_comment" to LslColorKeys.BLOCK_COMMENT,
        "operation_sign" to LslColorKeys.OPERATION_SIGN,
        "braces" to LslColorKeys.BRACES,
        "dot" to LslColorKeys.DOT,
        "semicolon" to LslColorKeys.SEMICOLON,
        "comma" to LslColorKeys.COMMA,
        "parentheses" to LslColorKeys.PARENTHESES,
        "brackets" to LslColorKeys.BRACKETS,
        "label" to LslColorKeys.LABEL,
    )

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = LslLanguage.INSTANCE.displayName

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keywords//Keyword", LslColorKeys.KEYWORD),
            AttributesDescriptor("Keywords//Type", LslColorKeys.TYPE),
            AttributesDescriptor("Functions//Built-in function", LslColorKeys.BUILTIN_FUNCTION),
            AttributesDescriptor("Constants//Built-in constant", LslColorKeys.BUILTIN_CONSTANT),
            AttributesDescriptor("Constants//Constant", LslColorKeys.CONSTANT),
            AttributesDescriptor("Events//Event handler", LslColorKeys.EVENT),
            AttributesDescriptor("Identifiers//Identifier", LslColorKeys.IDENTIFIER),
            AttributesDescriptor("Comments//Line comment", LslColorKeys.LINE_COMMENT),
            AttributesDescriptor("Comments//Block comment", LslColorKeys.BLOCK_COMMENT),
            AttributesDescriptor("Literals//Number", LslColorKeys.NUMBER),
            AttributesDescriptor("Literals//String", LslColorKeys.STRING),
            AttributesDescriptor("Braces and Operators//Braces", LslColorKeys.BRACES),
            AttributesDescriptor("Braces and Operators//Brackets", LslColorKeys.BRACKETS),
            AttributesDescriptor("Braces and Operators//Parentheses", LslColorKeys.PARENTHESES),
            AttributesDescriptor("Braces and Operators//Comma", LslColorKeys.COMMA),
            AttributesDescriptor("Braces and Operators//Semicolon", LslColorKeys.SEMICOLON),
            AttributesDescriptor("Braces and Operators//Dot", LslColorKeys.DOT),
            AttributesDescriptor("Braces and Operators//Operator sign", LslColorKeys.OPERATION_SIGN),
            AttributesDescriptor("Labels//Label", LslColorKeys.LABEL),
        )
    }
}
