package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.LslPrimitiveType
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslLValue

private val VECTOR_COMPONENTS = setOf("x", "y", "z")
private val QUATERNION_COMPONENTS = setOf("x", "y", "z", "s")

class LslInvalidVectorOrQuaternionItemInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Invalid vector or quaternion item"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val preprocessorEngine = file.project.getService(LslPreprocessorEngine::class.java)

        return object : LslElementVisitor() {

            override fun visitElement(element: PsiElement) {
                if (element !is LslLValue) return
                if (element.textRange.isEmpty) return
                if (preprocessorEngine.isDisabledText(file, element.textRange)) return

                val item = element.item
                if (item.isNullOrBlank()) return

                val isInvalid = when (element.variable?.lslType) {
                    LslPrimitiveType.VECTOR -> item !in VECTOR_COMPONENTS
                    LslPrimitiveType.QUATERNION -> item !in QUATERNION_COMPONENTS
                    else -> false
                }

                if (isInvalid) {
                    val dot = element.dot
                    val highlightRange = TextRange(
                        dot?.startOffsetInParent ?: 0,
                        element.textLength
                    )

                    holder.registerProblem(
                        element,
                        "Invalid item",
                        ProblemHighlightType.ERROR,
                        highlightRange,
                        RemoveLValueItem(element)
                    )
                }
            }
        }
    }

    class RemoveLValueItem(lvalue: LslLValue) : LocalQuickFixOnPsiElement(lvalue) {
        override fun getFamilyName(): String = "Remove item"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val lValue = startElement as? LslLValue ?: return
            val dot = lValue.dot ?: return
            lValue.deleteChildRange(dot, lValue.lastChild)
        }
    }
}