package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslExpressionFunctionCall

class LslUndeclaredFunctionInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Undeclared function"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val preprocessorEngine = file.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {

            override fun visitElement(element: PsiElement) {
                // 1. Guard check: only process function calls
                if (element !is LslExpressionFunctionCall) return
                if (element.textRange.isEmpty) return
                if (preprocessorEngine.isDisabledText(file, element.textRange)) return

                // 2. Locate function identifier
                val identifier = element.functionNameIdentifier ?: return

                // 3. Resolve reference from identifier OR call expression
                val reference = identifier.reference ?: element.reference

                // 4. Flag if no reference exists or resolution yields null
                if (reference == null || reference.resolve() == null) {
                    holder.registerProblem(
                        element,
                        "Undeclared function '${identifier.text}'",
                        ProblemHighlightType.ERROR,
                        identifier.textRangeInParent
                    )
                }
            }
        }
    }
}