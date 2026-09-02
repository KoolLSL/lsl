package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parents
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.*

private val FINAL_FUNCTIONS = setOf("llDie", "llResetScript")

class LslUnreachableCodeInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Unreachable code"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val preprocessorEngine = file.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {

            override fun visitElement(element: PsiElement) {
                if (element !is LslStatementBlock) return
                if (preprocessorEngine.isDisabledText(file, element.textRange)) return

                val unreachableCodeRanges = findUnreachableCodeRanges(element)

                unreachableCodeRanges.forEach { range ->
                    val first = range.first()
                    val last = range.last()

                    holder.registerProblem(
                        element,
                        "Unreachable code",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        TextRange(first.startOffsetInParent, last.textRangeInParent.endOffset),
                        RemoveUnreachableCodeFix(first, last)
                    )
                }
            }
        }
    }

    private fun findUnreachableCodeRanges(block: LslStatementBlock): List<Array<PsiElement>> {
        var isReachable = true
        val currentUnreachableBlock = ArrayList<PsiElement>()
        val result = ArrayList<Array<PsiElement>>()

        val parentScope = block.parents(false)
            .firstOrNull { it is LslFunction || it is LslEvent } ?: return emptyList()

        val searchScope = LocalSearchScope(parentScope)

        block.children.forEach { element ->
            if (!isReachable && element is LslStatementLabel && ReferencesSearch.search(element, searchScope).findFirst() != null) {
                isReachable = true

                if (currentUnreachableBlock.isNotEmpty()) {
                    result.add(currentUnreachableBlock.toTypedArray())
                    currentUnreachableBlock.clear()
                }
            }

            if (!isReachable) {
                currentUnreachableBlock.add(element)
            }

            isReachable = isReachable && when (element) {
                is LslStatementReturn -> false
                is LslStatementState -> false
                is LslStatementExpression -> !isFinalFunctionCall(element.children.singleOrNull() as? LslExpressionFunctionCall)
                else -> true
            }
        }

        if (currentUnreachableBlock.isNotEmpty()) {
            result.add(currentUnreachableBlock.toTypedArray())
            currentUnreachableBlock.clear()
        }

        return result
    }

    private fun isFinalFunctionCall(functionCall: LslExpressionFunctionCall?): Boolean {
        val functionName = functionCall?.functionName ?: return false
        return functionName in FINAL_FUNCTIONS
    }

    class RemoveUnreachableCodeFix(startElement: PsiElement, endElement: PsiElement) :
        LocalQuickFixOnPsiElement(startElement, endElement) {

        override fun getFamilyName(): String = "Remove unreachable code"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.parent.deleteChildRange(startElement, endElement)
        }
    }
}