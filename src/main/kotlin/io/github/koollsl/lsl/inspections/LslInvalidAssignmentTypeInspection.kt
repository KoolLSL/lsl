package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.tree.IElementType
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.LslPrimitiveType
import io.github.koollsl.lsl.parser.LslTypes
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslExpression
import io.github.koollsl.lsl.psi.LslExpressionAssignment
import io.github.koollsl.lsl.psi.LslGlobalVariable
import io.github.koollsl.lsl.psi.LslStatementVariable

class LslInvalidAssignmentTypeInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Invalid assignment type"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = "Invalid assignment type"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val preprocessorEngine = file.project.getService(LslPreprocessorEngine::class.java)

        return object : LslElementVisitor() {

            override fun visitExpressionAssignment(assignment: LslExpressionAssignment) {
                checkAssignment(
                    element = assignment,
                    variableType = assignment.lValue?.lslType,
                    expression = assignment.expression,
                    operator = assignment.operator,
                    preprocessorEngine = preprocessorEngine,
                    holder = holder
                )
            }

            override fun visitGlobalVariable(variable: LslGlobalVariable) {
                checkAssignment(
                    element = variable,
                    variableType = variable.lslType,
                    expression = variable.expression,
                    operator = LslTypes.ASSIGN,
                    preprocessorEngine = preprocessorEngine,
                    holder = holder
                )
            }

            override fun visitStatementVariable(variable: LslStatementVariable) {
                checkAssignment(
                    element = variable,
                    variableType = variable.lslType,
                    expression = variable.expression,
                    operator = LslTypes.ASSIGN,
                    preprocessorEngine = preprocessorEngine,
                    holder = holder
                )
            }
        }
    }

    private fun checkAssignment(
        element: PsiElement,
        variableType: LslPrimitiveType?,
        expression: LslExpression?,
        operator: IElementType?,
        preprocessorEngine: LslPreprocessorEngine,
        holder: ProblemsHolder
    ) {
        if (variableType == null || expression == null) return
        if (preprocessorEngine.isDisabledText(holder.file, element.textRange)) return

        val expressionType = expression.lslType ?: LslPrimitiveType.INVALID

        if (expressionType != LslPrimitiveType.INVALID &&
            variableType.operationTo(expressionType, operator) == LslPrimitiveType.INVALID
        ) {
            holder.registerProblem(
                expression,
                "Invalid assignment type (expected %s, got %s)".format(variableType, expressionType),
                ProblemHighlightType.GENERIC_ERROR
            )
        }
    }
}