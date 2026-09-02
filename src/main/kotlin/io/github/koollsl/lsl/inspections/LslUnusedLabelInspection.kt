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
import io.github.koollsl.lsl.psi.LslStatementLabel

class LslUnusedLabelInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Unused label"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val preprocessorEngine = file.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {

            override fun visitElement(element: PsiElement) {
                if (element !is LslStatementLabel) return
                if (element.textRange.isEmpty) return
                if (preprocessorEngine.isDisabledText(file, element.textRange)) return

                // Scope label searches to the containing function or event body
                val parentScope = element.parents(false)
                    .firstOrNull { it is LslFunction || it is LslEvent } ?: file
                val searchScope = LocalSearchScope(parentScope)

                if (ReferencesSearch.search(element, searchScope).findFirst() == null) {
                    holder.registerProblem(
                        element,
                        "Unused label",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        element.identifyingElement?.textRangeInParent,
                        RemoveUnusedLabelFix(element)
                    )
                }
            }
        }
    }

    class RemoveUnusedLabelFix(label: LslStatementLabel) : LocalQuickFixOnPsiElement(label) {
        override fun getFamilyName(): String = "Remove unused label"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.delete()
        }
    }
}