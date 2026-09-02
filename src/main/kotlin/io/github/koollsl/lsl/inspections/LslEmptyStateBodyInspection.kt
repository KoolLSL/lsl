package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslState

class LslEmptyStateBodyInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Empty state"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String =
        "Reports LSL state blocks that do not contain any event handlers."

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val preprocessorEngine = file.project.getService(LslPreprocessorEngine::class.java)

        return object : LslElementVisitor() {
            override fun visitState(state: LslState) {
                if (preprocessorEngine.isDisabledText(file, state.textRange)) return

                if (state.events.isEmpty()) {
                    holder.registerProblem(
                        state,
                        "State has no events",
                        ProblemHighlightType.ERROR,
                        TextRange(state.braceLeftEl?.startOffsetInParent ?: 0, state.textLength)
                    )
                }
            }
        }
    }
}