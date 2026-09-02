package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.LslPrimitiveType
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslExpressionTypeCast

class LslRedundantTypeCastInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Redundant type cast"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val engine = file.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {

            override fun visitElement(element: PsiElement) {
                if (element !is LslExpressionTypeCast) return
                if (element.textRange.isEmpty) return
                if (engine.isElementDisabled(element)) return

                val targetType = element.lslType
                if (targetType == LslPrimitiveType.INVALID) return

                val innerExpression = element.expression ?: return
                val expressionType = innerExpression.lslType ?: LslPrimitiveType.INVALID

                if (targetType == expressionType) {
                    val endOffset = element.parenthesesRightEl?.textRangeInParent?.endOffset
                        ?: innerExpression.textRangeInParent.startOffset

                    val highlightRange = TextRange(0, endOffset)

                    holder.registerProblem(
                        element,
                        "Redundant type cast",
                        ProblemHighlightType.WEAK_WARNING,
                        highlightRange,
                        RemoveRedundantTypeCastFix(element)
                    )
                }
            }
        }
    }

    class RemoveRedundantTypeCastFix(typeCast: LslExpressionTypeCast) : LocalQuickFixOnPsiElement(typeCast) {
        override fun getFamilyName(): String = "Remove redundant type cast"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val typeCast = startElement as? LslExpressionTypeCast ?: return
            val expression = typeCast.expression ?: return
            typeCast.replace(expression)
        }
    }
}