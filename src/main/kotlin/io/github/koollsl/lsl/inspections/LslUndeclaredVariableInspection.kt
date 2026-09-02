package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslLValue

class LslUndeclaredVariableInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Undeclared variable"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val preprocessorEngine = file.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {

            override fun visitElement(element: PsiElement) {
                if (element !is LslLValue) return
                if (element.textRange.isEmpty) return
                if (preprocessorEngine.isDisabledText(file, element.textRange)) return

                if (element.reference?.resolve() == null) {
                    holder.registerProblem(
                        element,
                        "Undeclared variable",
                        ProblemHighlightType.ERROR,
                        element.variableNameIdentifier?.textRangeInParent
                        // TODO: create variable fix
                    )
                }
            }
        }
    }
}