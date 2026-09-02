package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import io.github.koollsl.lsl.KwdbData
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslEvent
import io.github.koollsl.lsl.psi.LslNamedElement

class LslReservedIdentifierInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Reserved identifier"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val engine = file.project.service<LslPreprocessorEngine>()

        val kwdbData = KwdbData.getInstance(file.project)
        val kwdbNames = kwdbData.constants.keys + kwdbData.functions.keys + kwdbData.events.keys

        return object : LslElementVisitor() {

            override fun visitElement(element: PsiElement) {
                if (element !is LslNamedElement) return
                if (element is LslEvent) return
                if (element.textRange.isEmpty) return
                if (engine.isElementDisabled(element)) return

                val name = element.name ?: return

                if (kwdbNames.contains(name)) {
                    holder.registerProblem(
                        element,
                        "Reserved identifier",
                        ProblemHighlightType.GENERIC_ERROR,
                        element.identifyingElement?.textRangeInParent
                    )
                }
            }
        }
    }
}