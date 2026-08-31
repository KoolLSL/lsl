package io.github.koollsl.lsl.annotation

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine

class LslDisabledCodeInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        val engine = element.project.service<LslPreprocessorEngine>()
        return engine.isElementDisabled(element)
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
        SuppressQuickFix.EMPTY_ARRAY
}
