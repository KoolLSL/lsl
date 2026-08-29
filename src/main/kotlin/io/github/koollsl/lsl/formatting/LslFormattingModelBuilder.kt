package io.github.koollsl.lsl.formatting

import com.intellij.formatting.*
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.tree.TokenSet
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.parser.LslTypes

class LslFormattingModelBuilder : FormattingModelBuilder {
    val identifierSet = TokenSet.create(LslTypes.IDENTIFIER)
    val parenthesesRightSet = TokenSet.create(LslTypes.PARENTHESES_RIGHT)
    val returnSet = TokenSet.create(LslTypes.RETURN)
    val statementsWithoutBlock = TokenSet.create(
        LslTypes.STATEMENT_EMPTY,
        LslTypes.STATEMENT_VARIABLE,
        LslTypes.STATEMENT_LABEL,
        LslTypes.STATEMENT_JUMP,
        LslTypes.STATEMENT_STATE,
        LslTypes.STATEMENT_RETURN,
        LslTypes.STATEMENT_IF,
        LslTypes.STATEMENT_WHILE,
        LslTypes.STATEMENT_DO,
        LslTypes.STATEMENT_FOR,
        LslTypes.STATEMENT_EXPRESSION,
    )

    fun createSpacingBuilder(settings: CodeStyleSettings): SpacingBuilder {
        val commonSettings = settings.getCommonSettings(LslLanguage.INSTANCE)

        val isClassNextLine = commonSettings.CLASS_BRACE_STYLE == com.intellij.psi.codeStyle.CommonCodeStyleSettings.NEXT_LINE ||
                commonSettings.CLASS_BRACE_STYLE == com.intellij.psi.codeStyle.CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
                commonSettings.CLASS_BRACE_STYLE == com.intellij.psi.codeStyle.CommonCodeStyleSettings.NEXT_LINE_SHIFTED2

        val isMethodNextLine = commonSettings.METHOD_BRACE_STYLE == com.intellij.psi.codeStyle.CommonCodeStyleSettings.NEXT_LINE ||
                commonSettings.METHOD_BRACE_STYLE == com.intellij.psi.codeStyle.CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
                commonSettings.METHOD_BRACE_STYLE == com.intellij.psi.codeStyle.CommonCodeStyleSettings.NEXT_LINE_SHIFTED2

        val isBlockNextLine = commonSettings.BRACE_STYLE == com.intellij.psi.codeStyle.CommonCodeStyleSettings.NEXT_LINE ||
                commonSettings.BRACE_STYLE == com.intellij.psi.codeStyle.CommonCodeStyleSettings.NEXT_LINE_SHIFTED ||
                commonSettings.BRACE_STYLE == com.intellij.psi.codeStyle.CommonCodeStyleSettings.NEXT_LINE_SHIFTED2

        var builder = SpacingBuilder(settings, LslLanguage.INSTANCE)
            .after(LslTypes.LINE_COMMENT).lineBreakInCode()
            .around(LslTypes.COMMENTS).spaceIf(true)

            .around(LslTypes.FUNCTION).blankLines(commonSettings.BLANK_LINES_AROUND_METHOD)
            .around(LslTypes.DEFAULT_STATE_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_CLASS)
            .around(LslTypes.STATE_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_CLASS)
            .between(LslTypes.EVENT, LslTypes.EVENT).blankLines(commonSettings.BLANK_LINES_AROUND_METHOD_IN_INTERFACE)

            .before(LslTypes.ELSE).lineBreakInCodeIf(commonSettings.ELSE_ON_NEW_LINE)
            .beforeInside(LslTypes.WHILE, LslTypes.STATEMENT_DO).lineBreakInCodeIf(commonSettings.WHILE_ON_NEW_LINE)

            .aroundInside(LslTypes.ASSIGNMENT_OPERATORS, LslTypes.EXPRESSION_ASSIGNMENT)
            .spaceIf(commonSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS)
            .aroundInside(LslTypes.ASSIGNMENT_OPERATORS, LslTypes.EXPRESSION_BINARY)
            .spaceIf(commonSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS)
            .aroundInside(LslTypes.ASSIGNMENT_OPERATORS, LslTypes.GLOBAL_VARIABLE)
            .spaceIf(commonSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS)
            .aroundInside(LslTypes.ASSIGNMENT_OPERATORS, LslTypes.STATEMENT_VARIABLE)
            .spaceIf(commonSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS)

            .aroundInside(LslTypes.LOGICAL_OPERATORS, LslTypes.EXPRESSION_BINARY)
            .spaceIf(commonSettings.SPACE_AROUND_LOGICAL_OPERATORS)
            .aroundInside(LslTypes.EQUALITY_OPERATORS, LslTypes.EXPRESSION_BINARY)
            .spaceIf(commonSettings.SPACE_AROUND_EQUALITY_OPERATORS)
            .aroundInside(LslTypes.RELATIONAL_OPERATORS, LslTypes.EXPRESSION_BINARY)
            .spaceIf(commonSettings.SPACE_AROUND_RELATIONAL_OPERATORS)
            .aroundInside(LslTypes.BITWISE_OPERATORS, LslTypes.EXPRESSION_BINARY)
            .spaceIf(commonSettings.SPACE_AROUND_BITWISE_OPERATORS)
            .aroundInside(LslTypes.ADDITIVE_OPERATORS, LslTypes.EXPRESSION_BINARY)
            .spaceIf(commonSettings.SPACE_AROUND_ADDITIVE_OPERATORS)
            .aroundInside(LslTypes.MULTIPLICATIVE_OPERATORS, LslTypes.EXPRESSION_BINARY)
            .spaceIf(commonSettings.SPACE_AROUND_MULTIPLICATIVE_OPERATORS)
            .aroundInside(LslTypes.SHIFT_OPERATORS, LslTypes.EXPRESSION_BINARY)
            .spaceIf(commonSettings.SPACE_AROUND_SHIFT_OPERATORS)

            .aroundInside(LslTypes.OPERATORS, LslTypes.GLOBAL_VARIABLE).spaceIf(false)
            .aroundInside(LslTypes.OPERATORS, LslTypes.EXPRESSION_UNARY_PREFIX).spaceIf(false)
            .aroundInside(LslTypes.OPERATORS, LslTypes.EXPRESSION_UNARY_POSTFIX).spaceIf(false)

            .before(LslTypes.SEMICOLON).spaceIf(false)
            .after(LslTypes.COMMA).spaceIf(commonSettings.SPACE_AFTER_COMMA)
            .before(LslTypes.COMMA).spaceIf(commonSettings.SPACE_BEFORE_COMMA)
            .betweenInside(LslTypes.SEMICOLON, LslTypes.SEMICOLON, LslTypes.STATEMENT_FOR).spaceIf(false)
            .betweenInside(LslTypes.SEMICOLON, LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_FOR).spaceIf(false)
            .beforeInside(LslTypes.SEMICOLON, LslTypes.STATEMENT_FOR).spaceIf(commonSettings.SPACE_BEFORE_SEMICOLON)
            .afterInside(LslTypes.SEMICOLON, LslTypes.STATEMENT_FOR).spaceIf(commonSettings.SPACE_AFTER_SEMICOLON)

            .aroundInside(LslTypes.EXPRESSIONS, LslTypes.EXPRESSION_PARENTHESES)
            .spaceIf(commonSettings.SPACE_WITHIN_PARENTHESES)
            .betweenInside(LslTypes.PARENTHESES_LEFT, LslTypes.PARENTHESES_RIGHT, LslTypes.EXPRESSION_FUNCTION_CALL)
            .spaceIf(commonSettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES)
            .afterInside(LslTypes.PARENTHESES_LEFT, LslTypes.EXPRESSION_FUNCTION_CALL)
            .spaceIf(commonSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES)
            .beforeInside(LslTypes.PARENTHESES_RIGHT, LslTypes.EXPRESSION_FUNCTION_CALL)
            .spaceIf(commonSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES)
            .afterInside(LslTypes.PARENTHESES_LEFT, LslTypes.EXPRESSION_PRINT)
            .spaceIf(commonSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES)
            .beforeInside(LslTypes.PARENTHESES_RIGHT, LslTypes.EXPRESSION_PRINT)
            .spaceIf(commonSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES)
            .afterInside(LslTypes.PARENTHESES_LEFT, LslTypes.STATEMENT_IF)
            .spaceIf(commonSettings.SPACE_WITHIN_IF_PARENTHESES)
            .beforeInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_IF)
            .spaceIf(commonSettings.SPACE_WITHIN_IF_PARENTHESES)
            .afterInside(LslTypes.PARENTHESES_LEFT, LslTypes.STATEMENT_WHILE)
            .spaceIf(commonSettings.SPACE_WITHIN_WHILE_PARENTHESES)
            .beforeInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_WHILE)
            .spaceIf(commonSettings.SPACE_WITHIN_WHILE_PARENTHESES)
            .afterInside(LslTypes.PARENTHESES_LEFT, LslTypes.STATEMENT_DO)
            .spaceIf(commonSettings.SPACE_WITHIN_WHILE_PARENTHESES)
            .beforeInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_DO)
            .spaceIf(commonSettings.SPACE_WITHIN_WHILE_PARENTHESES)
            .afterInside(LslTypes.PARENTHESES_LEFT, LslTypes.STATEMENT_FOR)
            .spaceIf(commonSettings.SPACE_WITHIN_FOR_PARENTHESES)
            .beforeInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_FOR)
            .spaceIf(commonSettings.SPACE_WITHIN_FOR_PARENTHESES)
            .aroundInside(LslTypes.TYPE_NAME, LslTypes.EXPRESSION_TYPE_CAST)
            .spaceIf(commonSettings.SPACE_WITHIN_CAST_PARENTHESES)
            .afterInside(LslTypes.BRACKET_LEFT, LslTypes.EXPRESSION_LIST).spaceIf(commonSettings.SPACE_WITHIN_BRACKETS)
            .beforeInside(LslTypes.BRACKET_RIGHT, LslTypes.EXPRESSION_LIST)
            .spaceIf(commonSettings.SPACE_WITHIN_BRACKETS)
            .afterInside(LslTypes.BRACE_LEFT, LslTypes.STATEMENT_BLOCK).spaceIf(commonSettings.SPACE_WITHIN_BRACES)
            .beforeInside(LslTypes.BRACE_RIGHT, LslTypes.STATEMENT_BLOCK).spaceIf(commonSettings.SPACE_WITHIN_BRACES)

            .betweenInside(parenthesesRightSet, statementsWithoutBlock, LslTypes.STATEMENT_IF).spaceIf(true)
            .betweenInside(parenthesesRightSet, statementsWithoutBlock, LslTypes.STATEMENT_WHILE).spaceIf(true)
            .betweenInside(parenthesesRightSet, statementsWithoutBlock, LslTypes.STATEMENT_FOR).spaceIf(true)
            .betweenInside(LslTypes.PARENTHESES, LslTypes.EXPRESSIONS, LslTypes.EXPRESSION_TYPE_CAST)
            .spaceIf(commonSettings.SPACE_AFTER_TYPE_CAST)
            .betweenInside(identifierSet, LslTypes.PARENTHESES, LslTypes.EXPRESSION_FUNCTION_CALL)
            .spaceIf(commonSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES)
            .betweenInside(identifierSet, LslTypes.PARENTHESES, LslTypes.FUNCTION)
            .spaceIf(commonSettings.SPACE_BEFORE_METHOD_PARENTHESES)
            .betweenInside(identifierSet, LslTypes.PARENTHESES, LslTypes.EVENT)
            .spaceIf(commonSettings.SPACE_BEFORE_METHOD_PARENTHESES)
            .betweenInside(LslTypes.IF, LslTypes.PARENTHESES_LEFT, LslTypes.STATEMENT_IF)
            .spaceIf(commonSettings.SPACE_BEFORE_IF_PARENTHESES)
            .betweenInside(LslTypes.WHILE, LslTypes.PARENTHESES_LEFT, LslTypes.STATEMENT_WHILE)
            .spaceIf(commonSettings.SPACE_BEFORE_WHILE_PARENTHESES)
            .betweenInside(LslTypes.WHILE, LslTypes.PARENTHESES_LEFT, LslTypes.STATEMENT_DO)
            .spaceIf(commonSettings.SPACE_BEFORE_WHILE_PARENTHESES)
            .betweenInside(LslTypes.FOR, LslTypes.PARENTHESES_LEFT, LslTypes.STATEMENT_FOR)
            .spaceIf(commonSettings.SPACE_BEFORE_FOR_PARENTHESES)

            .betweenInside(LslTypes.STATE, LslTypes.IDENTIFIER, LslTypes.STATE_DECLARATION).spaces(1)
            .betweenInside(LslTypes.STATE, LslTypes.IDENTIFIER, LslTypes.STATEMENT_STATE).spaces(1)
            .betweenInside(LslTypes.STATE, LslTypes.DEFAULT, LslTypes.STATEMENT_STATE).spaces(1)
            .betweenInside(LslTypes.JUMP, LslTypes.IDENTIFIER, LslTypes.STATEMENT_JUMP).spaces(1)
            .betweenInside(returnSet, LslTypes.EXPRESSIONS, LslTypes.STATEMENT_RETURN).spaces(1)
            .betweenInside(LslTypes.TYPE_NAME, LslTypes.IDENTIFIER, LslTypes.GLOBAL_VARIABLE).spaces(1)
            .betweenInside(LslTypes.TYPE_NAME, LslTypes.IDENTIFIER, LslTypes.STATEMENT_VARIABLE).spaces(1)
            .betweenInside(LslTypes.TYPE_NAME, LslTypes.IDENTIFIER, LslTypes.ARGUMENT).spaces(1)
            .betweenInside(LslTypes.TYPE_NAME, LslTypes.IDENTIFIER, LslTypes.FUNCTION).spaces(1)

        if (isClassNextLine) {
            builder = builder
                .betweenInside(LslTypes.DEFAULT, LslTypes.BRACE_LEFT, LslTypes.DEFAULT_STATE_DECLARATION).lineBreakInCode()
                .betweenInside(LslTypes.IDENTIFIER, LslTypes.BRACE_LEFT, LslTypes.STATE_DECLARATION).lineBreakInCode()
        } else {
            builder = builder
                .betweenInside(LslTypes.DEFAULT, LslTypes.BRACE_LEFT, LslTypes.DEFAULT_STATE_DECLARATION).spaceIf(commonSettings.SPACE_BEFORE_CLASS_LBRACE)
                .betweenInside(LslTypes.IDENTIFIER, LslTypes.BRACE_LEFT, LslTypes.STATE_DECLARATION).spaceIf(commonSettings.SPACE_BEFORE_CLASS_LBRACE)
        }

        if (isMethodNextLine) {
            builder = builder
                .betweenInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_BLOCK, LslTypes.FUNCTION).lineBreakInCode()
                .betweenInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_BLOCK, LslTypes.EVENT).lineBreakInCode()
        } else {
            builder = builder
                .betweenInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_BLOCK, LslTypes.FUNCTION).spaceIf(commonSettings.SPACE_BEFORE_METHOD_LBRACE)
                .betweenInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_BLOCK, LslTypes.EVENT).spaceIf(commonSettings.SPACE_BEFORE_METHOD_LBRACE)
        }

        if (isBlockNextLine) {
            builder = builder
                .betweenInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_BLOCK, LslTypes.STATEMENT_IF).lineBreakInCode()
                .betweenInside(LslTypes.ELSE, LslTypes.STATEMENT_BLOCK, LslTypes.STATEMENT_ELSE).lineBreakInCode()
                .betweenInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_BLOCK, LslTypes.STATEMENT_WHILE).lineBreakInCode()
                .betweenInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_BLOCK, LslTypes.STATEMENT_FOR).lineBreakInCode()
                .betweenInside(LslTypes.DO, LslTypes.STATEMENT_BLOCK, LslTypes.STATEMENT_DO).lineBreakInCode()
        } else {
            builder = builder
                .betweenInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_BLOCK, LslTypes.STATEMENT_IF).spaceIf(commonSettings.SPACE_BEFORE_IF_LBRACE)
                .betweenInside(LslTypes.ELSE, LslTypes.STATEMENT_BLOCK, LslTypes.STATEMENT_ELSE).spaceIf(commonSettings.SPACE_BEFORE_ELSE_LBRACE)
                .betweenInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_BLOCK, LslTypes.STATEMENT_WHILE).spaceIf(commonSettings.SPACE_BEFORE_WHILE_LBRACE)
                .betweenInside(LslTypes.PARENTHESES_RIGHT, LslTypes.STATEMENT_BLOCK, LslTypes.STATEMENT_FOR).spaceIf(commonSettings.SPACE_BEFORE_FOR_LBRACE)
                .betweenInside(LslTypes.DO, LslTypes.STATEMENT_BLOCK, LslTypes.STATEMENT_DO).spaceIf(commonSettings.SPACE_BEFORE_DO_LBRACE)
        }

        builder = if (commonSettings.ELSE_ON_NEW_LINE) {
            builder.beforeInside(LslTypes.STATEMENT_ELSE, LslTypes.STATEMENT_IF).lineBreakInCode()
        } else {
            builder.beforeInside(LslTypes.STATEMENT_ELSE, LslTypes.STATEMENT_IF).spaceIf(commonSettings.SPACE_BEFORE_ELSE_KEYWORD)
        }

        builder = if (commonSettings.WHILE_ON_NEW_LINE) {
            builder.beforeInside(LslTypes.WHILE, LslTypes.STATEMENT_DO).lineBreakInCode()
        } else {
            builder.beforeInside(LslTypes.WHILE, LslTypes.STATEMENT_DO).spaceIf(commonSettings.SPACE_BEFORE_WHILE_KEYWORD)
        }

        return builder
    }

    override fun createModel(formattingContext: FormattingContext): FormattingModel =
        FormattingModelProvider.createFormattingModelForPsiFile(
            formattingContext.containingFile,
            LslBlock(
                null,
                formattingContext.node,
                null,
                null,
                Indent.getNoneIndent(),
                createSpacingBuilder(formattingContext.codeStyleSettings),
                formattingContext.codeStyleSettings.getCommonSettings(LslLanguage.INSTANCE),
            ),
            formattingContext.codeStyleSettings,
        )
}