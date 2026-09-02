package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslNamedElement
import io.github.koollsl.lsl.references.LslReferenceUtils

class LslRedeclaredIdentifierInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Redeclared identifier"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val engine = file.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {

            override fun visitElement(element: PsiElement) {
                if (element !is LslNamedElement) return
                if (element.textRange.isEmpty) return
                if (engine.isElementDisabled(element)) return

                val name = element.name ?: return
                val existingIdentifier = LslReferenceUtils.findNamedElement(element, name) ?: return

                if (existingIdentifier == element || engine.isElementDisabled(existingIdentifier)) {
                    return
                }

                val highlightType = if (existingIdentifier.parent == element.parent) {
                    ProblemHighlightType.GENERIC_ERROR
                } else {
                    ProblemHighlightType.WARNING
                }

                holder.registerProblem(
                    element,
                    "Redeclared identifier",
                    highlightType,
                    element.identifyingElement?.textRangeInParent,
                    NavigateToElementFix(existingIdentifier)
                )
            }
        }
    }

    class NavigateToElementFix(element: PsiElement) : LocalQuickFixOnPsiElement(element) {
        override fun startInWriteAction(): Boolean = false

        override fun getText(): String = familyName

        override fun getFamilyName(): String = "Navigate to previous declaration"

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            if (startElement is Navigatable) {
                startElement.navigate(true)
            }
        }
    }
}