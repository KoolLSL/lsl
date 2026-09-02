package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parents
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslEvent
import io.github.koollsl.lsl.psi.LslFunction
import io.github.koollsl.lsl.psi.LslGlobalVariable
import io.github.koollsl.lsl.psi.LslStatementVariable
import io.github.koollsl.lsl.psi.LslVariable

class LslUnusedVariableInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Unused variable"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val preprocessorEngine = file.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {

            override fun visitElement(element: PsiElement) {
                if (element !is LslGlobalVariable && element !is LslStatementVariable) return
                if (element.textRange.isEmpty) return
                if (preprocessorEngine.isDisabledText(file, element.textRange)) return

                // Determine appropriate LocalSearchScope based on variable scope
                val searchScope = when (element) {
                    is LslGlobalVariable -> LocalSearchScope(file)
                    is LslStatementVariable -> {
                        val parentScope = element.parents(false)
                            .firstOrNull { it is LslFunction || it is LslEvent } ?: file
                        LocalSearchScope(parentScope)
                    }
                    else -> return
                }

                if (ReferencesSearch.search(element, searchScope).findFirst() == null) {
                    val targetVariable = element as LslVariable
                    holder.registerProblem(
                        element,
                        "Unused variable",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        targetVariable.identifyingElement?.textRangeInParent,
                        RemoveUnusedVariableFix(targetVariable)
                    )
                }
            }
        }
    }

    class RemoveUnusedVariableFix(variable: LslVariable) : LocalQuickFixOnPsiElement(variable) {
        override fun getFamilyName(): String = "Remove unused variable"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.delete()
        }
    }
}