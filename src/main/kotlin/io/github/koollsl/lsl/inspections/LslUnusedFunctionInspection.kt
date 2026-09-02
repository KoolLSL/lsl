package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslFunction

class LslUnusedFunctionInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Unused function"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val preprocessorEngine = file.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {

            override fun visitElement(element: PsiElement) {
                if (element !is LslFunction) return
                if (element.textRange.isEmpty) return
                if (preprocessorEngine.isDisabledText(file, element.textRange)) return

                // Restrict search scope to the containing file since LSL user functions are file-local
                val searchScope = LocalSearchScope(file)

                if (ReferencesSearch.search(element, searchScope).findFirst() == null) {
                    holder.registerProblem(
                        element,
                        "Unused function",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        element.identifyingElement?.textRangeInParent,
                        RemoveUnusedFunctionFix(element)
                    )
                }
            }
        }
    }

    class RemoveUnusedFunctionFix(function: LslFunction) : LocalQuickFixOnPsiElement(function) {
        override fun getFamilyName(): String = "Remove unused function"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.delete()
        }
    }
}