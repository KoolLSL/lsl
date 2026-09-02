package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.LslPrimitiveType
import io.github.koollsl.lsl.parser.LslTypes
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.*
import kotlin.math.min

class LslInvalidFunctionCallArgumentInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Invalid function call argument"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = "Invalid function call argument"

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
        val holder = ProblemsHolder(manager, file, isOnTheFly)
        val preprocessorEngine = file.project.getService(LslPreprocessorEngine::class.java)

        PsiTreeUtil.collectElementsOfType(file, LslExpressionFunctionCall::class.java)
            .asSequence()
            .filter { !preprocessorEngine.isDisabledText(file, it.textRange) }
            .forEach { call ->
                // Resolve function target (works for user and built-in LSL functions)
                val targetFunction = call.reference?.resolve() as? LslFunction ?: return@forEach
                val arguments = targetFunction.arguments
                val expressions = call.expressions

                // 1. Check for argument type mismatches
                if (expressions.isNotEmpty()) {
                    (0 until min(expressions.size, arguments.size)).forEach { i ->
                        val argumentType = arguments[i].lslType
                        val expression = expressions[i]
                        val expressionType = expression.lslType

                        if (argumentType.operationTo(expressionType, LslTypes.ASSIGN) == LslPrimitiveType.INVALID) {
                            holder.registerProblem(
                                expression,
                                "Type mismatch (expected %s, got %s)".format(argumentType, expressionType),
                                ProblemHighlightType.GENERIC_ERROR,
                                LslInvalidExpressionTypeInspection.TypeCastFix(expression, argumentType)
                            )
                        }
                    }
                }

                // 2. Check for parameter count mismatches
                if (expressions.size < arguments.size) {
                    val targetRange = call.parenthesesRightEl?.textRangeInParent
                        ?: call.lastChild.textRangeInParent

                    holder.registerProblem(
                        call,
                        "Wrong arguments count (expected ${arguments.size}, got ${expressions.size})",
                        ProblemHighlightType.GENERIC_ERROR,
                        targetRange
                    )
                }
                // 3. Check too many arguments
                else if (expressions.size > arguments.size) {
                    val firstExtraExpression = if (arguments.isNotEmpty()) {
                        expressions[arguments.size]
                    } else {
                        expressions.first()
                    }

                    val firstExtraExpressionComma = call.node.getChildren(null)
                        .filter { it.elementType == LslTypes.COMMA }
                        .lastOrNull { it.psi.endOffset < firstExtraExpression.startOffset }
                        ?.psi

                    val lastExtraExpression = expressions.last()

                    val targetRange = TextRange(
                        firstExtraExpressionComma?.textRangeInParent?.startOffset
                            ?: firstExtraExpression.textRangeInParent.startOffset,
                        lastExtraExpression.textRangeInParent.endOffset
                    )

                    holder.registerProblem(
                        call,
                        "Wrong arguments count (expected ${arguments.size}, got ${expressions.size})",
                        ProblemHighlightType.GENERIC_ERROR,
                        targetRange,
                        RemoveExtraArgumentsFix(
                            firstExtraExpressionComma ?: firstExtraExpression,
                            lastExtraExpression
                        )
                    )
                }
            }

        return holder.resultsArray
    }

    class RemoveExtraArgumentsFix(startElement: PsiElement, endElement: PsiElement) :
        LocalQuickFixOnPsiElement(startElement, endElement) {

        override fun getFamilyName(): String = "Remove extra arguments"

        override fun getText(): String = familyName

        override fun invoke(
            project: Project,
            file: PsiFile,
            startElement: PsiElement,
            endElement: PsiElement
        ) {
            startElement.parent.deleteChildRange(startElement, endElement)
        }
    }
}