package io.github.koollsl.lsl.annotation

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.psi.PsiErrorElement
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine

class LslPreprocessorErrorFilter : HighlightErrorFilter() {
    override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean {
        if (LslPreprocessorEngine.isElementDisabled(element)) {
            return false
        }
        return true
    }
}
