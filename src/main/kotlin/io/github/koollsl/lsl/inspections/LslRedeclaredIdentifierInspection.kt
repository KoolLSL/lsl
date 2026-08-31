package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslNamedElement
import io.github.koollsl.lsl.references.LslReferenceUtils

class LslRedeclaredIdentifierInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Redeclared identifier"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true

    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        isOnTheFly: Boolean
    ): Array<ProblemDescriptor> {

        val engine = file.project.service<LslPreprocessorEngine>()
        val problemsHolder = ProblemsHolder(manager, file, isOnTheFly)

        PsiTreeUtil.collectElementsOfType(file, LslNamedElement::class.java)
            .filter { !it.textRange.isEmpty && !engine.isElementDisabled(it) }
            .forEach { element ->
                val name = element.name ?: return@forEach
                val existingIdentifier = LslReferenceUtils.findNamedElement(element, name)
                    ?: return@forEach

                if (existingIdentifier == element ||
                    engine.isElementDisabled(existingIdentifier)
                ) {
                    return@forEach
                }

                problemsHolder.registerProblem(
                    element,
                    "Redeclared identifier",
                    if (existingIdentifier.parent == element.parent)
                        ProblemHighlightType.GENERIC_ERROR
                    else
                        ProblemHighlightType.WARNING,
                    element.identifyingElement?.textRangeInParent,
                    NavigateToElementFix(existingIdentifier),
                )
            }

        return problemsHolder.resultsArray
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