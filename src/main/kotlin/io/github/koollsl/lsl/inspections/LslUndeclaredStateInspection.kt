package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.psi.LslStatementState

class LslUndeclaredStateInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Undeclared state"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
        val problemsHolder = ProblemsHolder(manager, file, isOnTheFly)

        PsiTreeUtil.collectElementsOfType(file, LslStatementState::class.java)
            .filter { !it.textRange.isEmpty }
            .filter { it.reference?.resolve() == null }
            .forEach {
                problemsHolder.registerProblem(
                    it,
                    "Undeclared state",
                    ProblemHighlightType.ERROR,
                    it.stateNameIdentifier?.textRangeInParent,
                    // TODO: create variable fix
                )
            }

        return problemsHolder.resultsArray
    }
}