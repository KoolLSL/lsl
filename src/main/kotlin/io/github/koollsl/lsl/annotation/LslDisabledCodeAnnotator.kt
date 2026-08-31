package io.github.koollsl.lsl.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine

class LslDisabledCodeAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {

        if (element is PsiFile || element is PsiWhiteSpace) return

        val engine = element.project.service<LslPreprocessorEngine>()

        if (element.textLength > 0 &&
            element.firstChild == null &&
            engine.isElementDisabled(element)
        ) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES)
                .create()
        }
    }
}
