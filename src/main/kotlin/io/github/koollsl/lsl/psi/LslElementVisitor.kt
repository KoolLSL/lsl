package io.github.koollsl.lsl.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

open class LslElementVisitor : PsiElementVisitor() {
    open fun visitState(state: LslState) = visitPsiElement(state)
    open fun visitExpressionFunctionCall(call: LslExpressionFunctionCall) = visitPsiElement(call)
    open fun visitExpressionAssignment(assignment: LslExpressionAssignment) = visitPsiElement(assignment)
    open fun visitGlobalVariable(variable: LslGlobalVariable) = visitPsiElement(variable)
    open fun visitStatementVariable(variable: LslStatementVariable) = visitPsiElement(variable)

    override fun visitElement(element: PsiElement) {
        visitPsiElement(element)
    }

    open fun visitPsiElement(element: PsiElement) {
        // Fallback for default element handling
    }
}