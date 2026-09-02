package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.LslPrimitiveType
import io.github.koollsl.lsl.parser.LslTypes
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.*

class LslInvalidExpressionTypeInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Invalid expression type"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = "Invalid expression type"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val preprocessorEngine = file.project.getService(LslPreprocessorEngine::class.java)

        return object : LslElementVisitor() {

            // 1. Local Variable Initializations
            override fun visitStatementVariable(variable: LslStatementVariable) {
                if (preprocessorEngine.isDisabledText(file, variable.textRange)) return
                val declaredType = variable.lslType
                val initializer = variable.expression ?: return

                if (declaredType != LslPrimitiveType.INVALID) {
                    val actualType = initializer.lslType ?: LslPrimitiveType.INVALID
                    if (actualType != LslPrimitiveType.INVALID && declaredType.operationTo(actualType, LslTypes.ASSIGN) == LslPrimitiveType.INVALID) {
                        holder.registerProblem(
                            initializer,
                            "Type mismatch (expected %s, got %s)".format(declaredType, actualType),
                            ProblemHighlightType.GENERIC_ERROR,
                            TextRange(0, initializer.textLength),
                            TypeCastFix(initializer, declaredType)
                        )
                    }
                }
            }

            // 2. Global Variable Initializations
            override fun visitGlobalVariable(variable: LslGlobalVariable) {
                if (preprocessorEngine.isDisabledText(file, variable.textRange)) return
                val declaredType = variable.lslType
                val initializer = variable.expression ?: return

                if (declaredType != LslPrimitiveType.INVALID) {
                    val actualType = initializer.lslType ?: LslPrimitiveType.INVALID
                    if (actualType != LslPrimitiveType.INVALID && declaredType.operationTo(actualType, LslTypes.ASSIGN) == LslPrimitiveType.INVALID) {
                        holder.registerProblem(
                            initializer,
                            "Type mismatch (expected %s, got %s)".format(declaredType, actualType),
                            ProblemHighlightType.GENERIC_ERROR,
                            TextRange(0, initializer.textLength),
                            TypeCastFix(initializer, declaredType)
                        )
                    }
                }
            }

            // Router for PSI elements without dedicated visit methods in LslElementVisitor
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                if (preprocessorEngine.isDisabledText(file, element.textRange)) return

                when (element) {
                    // 3. Binary Expressions
                    is LslExpressionBinary -> {
                        val typeLeft = element.expressionLeft?.lslType ?: LslPrimitiveType.INVALID
                        val typeRight = element.expressionRight?.lslType ?: LslPrimitiveType.INVALID

                        if (typeLeft != LslPrimitiveType.INVALID && typeRight != LslPrimitiveType.INVALID &&
                            typeLeft.operationTo(typeRight, element.operator) == LslPrimitiveType.INVALID
                        ) {
                            val expressionRight = element.expressionRight
                            val fixes = listOfNotNull(
                                expressionRight?.let { TypeCastFix(it, typeLeft) }
                            ).toTypedArray()

                            holder.registerProblem(
                                element,
                                "Type mismatch (expected %s, got %s)".format(typeLeft, typeRight),
                                ProblemHighlightType.GENERIC_ERROR,
                                TextRange(0, element.textLength),
                                *fixes
                            )
                        }
                    }

                    // 4. Vector Components
                    is LslExpressionVector -> {
                        element.expressions.forEach { component ->
                            val expressionType = component.lslType
                            if (expressionType != LslPrimitiveType.INVALID &&
                                LslPrimitiveType.FLOAT.operationTo(expressionType, LslTypes.ASSIGN) == LslPrimitiveType.INVALID
                            ) {
                                holder.registerProblem(
                                    component,
                                    "Type mismatch (expected float, got %s)".format(expressionType),
                                    ProblemHighlightType.GENERIC_ERROR,
                                    TextRange(0, component.textLength),
                                    TypeCastFix(component, LslPrimitiveType.FLOAT)
                                )
                            }
                        }
                    }

                    // 5. Rotation/Quaternion Components
                    is LslExpressionQuaternion -> {
                        element.expressions.forEach { component ->
                            val expressionType = component.lslType
                            if (expressionType != LslPrimitiveType.INVALID &&
                                LslPrimitiveType.FLOAT.operationTo(expressionType, LslTypes.ASSIGN) == LslPrimitiveType.INVALID
                            ) {
                                holder.registerProblem(
                                    component,
                                    "Type mismatch (expected float, got %s)".format(expressionType),
                                    ProblemHighlightType.GENERIC_ERROR,
                                    TextRange(0, component.textLength),
                                    TypeCastFix(component, LslPrimitiveType.FLOAT)
                                )
                            }
                        }
                    }

                    // 6. Assignments in Conditions (if, while, do-while)
                    is LslStatementIf -> checkConditionForAssignment(element.condition)
                    is LslStatementWhile -> checkConditionForAssignment(element.condition)
                    is LslStatementDo -> checkConditionForAssignment(element.condition)
                }
            }

            private fun checkConditionForAssignment(condition: PsiElement?) {
                if (condition == null || preprocessorEngine.isDisabledText(file, condition.textRange)) return

                val queue = ArrayDeque<PsiElement>()
                queue.add(condition)

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()

                    val isAssignment = when (current) {
                        is LslExpressionBinary -> current.operator == LslTypes.ASSIGN
                        is LslExpressionAssignment -> true
                        else -> false
                    }

                    if (isAssignment) {
                        holder.registerProblem(
                            current,
                            "Assignment in condition (did you mean '=='?)",
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            TextRange(0, current.textLength)
                        )
                    }

                    queue.addAll(current.children)
                }
            }
        }
    }

    class TypeCastFix(expression: LslExpression, val type: LslPrimitiveType) : LocalQuickFixOnPsiElement(expression) {
        override fun getFamilyName(): String = "Cast to $type"
        override fun getText(): String = familyName

        override fun invoke(
            project: Project,
            file: PsiFile,
            startElement: PsiElement,
            endElement: PsiElement
        ) {
            val expression = startElement as? LslExpression ?: return
            when (expression) {
                is LslExpressionBinary -> {
                    expression.replace(
                        LslElementFactory.createTypeCast(
                            project,
                            type,
                            LslElementFactory.createParentheses(project, expression)
                        )
                    )
                }
                else -> {
                    expression.replace(
                        LslElementFactory.createTypeCast(
                            project,
                            type,
                            expression
                        )
                    )
                }
            }
        }
    }
}