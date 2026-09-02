package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.LslPrimitiveType
import io.github.koollsl.lsl.formatting.LslBlock
import io.github.koollsl.lsl.parser.LslTypes
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.*

class LslInvalidReturnTypeInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Invalid return type"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = "Invalid return type and missing return path inspection"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val preprocessorEngine = file.project.getService(LslPreprocessorEngine::class.java)

        return object : LslElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                if (preprocessorEngine.isDisabledText(file, element.textRange)) return

                when (element) {
                    is LslStatementReturn -> checkReturnStatement(element)
                    is LslFunction -> checkMissingReturnPath(element)
                }
            }

            private fun checkReturnStatement(element: LslStatementReturn) {
                val function = findEnclosingFunction(element)
                if (function != null) {
                    val expectedType = function.lslType
                    val expression = element.expression
                    val actualType = expression?.lslType ?: LslPrimitiveType.VOID

                    // Allow 'return;' or empty expressions in void functions
                    if (expectedType == LslPrimitiveType.VOID && actualType == LslPrimitiveType.VOID) {
                        return
                    }

                    if (expectedType.operationTo(actualType, LslTypes.ASSIGN) == LslPrimitiveType.INVALID) {
                        val fixes = listOfNotNull(
                            if (expression != null) TypeCastFix(expression, expectedType) else null
                        ).toTypedArray()

                        holder.registerProblem(
                            element,
                            "Type mismatch (expected %s, got %s)".format(expectedType, actualType),
                            ProblemHighlightType.GENERIC_ERROR,
                            TextRange(0, element.textLength),
                            *fixes
                        )
                    }
                } else {
                    val event = findEnclosingEvent(element)
                    if (event != null) {
                        if (element.expression != null) {
                            holder.registerProblem(
                                element,
                                "Events cannot return a value",
                                ProblemHighlightType.GENERIC_ERROR,
                                TextRange(0, element.textLength),
                                RemoveReturnExpressionFix(element)
                            )
                        }
                    }
                }
            }

            private fun checkMissingReturnPath(function: LslFunction) {
                val returnType = function.lslType ?: LslPrimitiveType.VOID
                if (returnType == LslPrimitiveType.VOID) return

                val body = function.body ?: return

                if (!guaranteesReturn(body)) {
                    val nameIdentifier = function.nameIdentifier ?: function
                    holder.registerProblem(
                        nameIdentifier,
                        "Missing return statement: function '%s' must return a value of type %s".format(function.name, returnType),
                        ProblemHighlightType.GENERIC_ERROR,
                        TextRange(0, nameIdentifier.textLength)
                    )
                }
            }

            private fun guaranteesReturn(element: PsiElement?): Boolean {
                if (element == null) return false

                return when (element) {
                    is LslStatementReturn -> true

                    is LslStatementBlock -> {
                        val children = element.children
                        for (i in children.indices.reversed()) {
                            if (guaranteesReturn(children[i])) return true
                        }
                        false
                    }

                    is LslStatementIf -> {
                        val thenBranch = element.statement
                        val elseBranch = element.statementElse

                        // An IF statement guarantees a return only if BOTH branches explicitly return
                        if (thenBranch != null && elseBranch != null) {
                            guaranteesReturn(thenBranch) && guaranteesReturn(elseBranch)
                        } else {
                            false
                        }
                    }

                    else -> {
                        // For wrapper nodes, check if any inner child path guarantees a return
                        var hasReturn = false
                        for (child in element.children) {
                            if (guaranteesReturn(child)) hasReturn = true
                        }
                        hasReturn
                    }
                }
            }        }
    }

    private fun findEnclosingFunction(element: PsiElement): LslFunction? {
        var current: PsiElement? = element.parent
        while (current != null && current !is PsiFile) {
            if (current is LslFunction) return current
            current = current.parent
        }
        return null
    }

    private fun findEnclosingEvent(element: PsiElement): LslEvent? {
        var current: PsiElement? = element.parent
        while (current != null && current !is PsiFile) {
            if (current is LslEvent) return current
            current = current.parent
        }
        return null
    }

    class TypeCastFix(expression: LslExpression, val type: LslPrimitiveType) : LocalQuickFixOnPsiElement(expression) {
        override fun getFamilyName(): String = "Cast to $type"
        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val expression = startElement as? LslExpression ?: return
            val newElement = LslElementFactory.createTypeCast(project, type, expression)
            expression.replace(newElement)
        }
    }

    class RemoveReturnExpressionFix(statementReturn: LslStatementReturn) : LocalQuickFixOnPsiElement(statementReturn) {
        override fun getFamilyName(): String = "Remove return value"
        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val statementReturn = startElement as? LslStatementReturn ?: return
            val dummyFile = LslElementFactory.createFile(project, "default { touch_start(integer n) { return; } }")
            val newStatement = PsiTreeUtil.findChildOfType(dummyFile, LslStatementReturn::class.java) ?: return
            statementReturn.replace(newStatement)
        }
    }
}