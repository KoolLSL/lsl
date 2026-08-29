package io.github.koollsl.lsl.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.koollsl.lsl.KwdbData
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslEvent
import io.github.koollsl.lsl.psi.LslExpressionFunctionCall
import io.github.koollsl.lsl.psi.LslLValue
import io.github.koollsl.lsl.syntax.LslColorKeys

class LslAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is PsiFile) {
            LslPreprocessorEngine.annotateIncludes(element, holder)
            return
        }

        if (LslPreprocessorEngine.isElementDisabled(element)) return

        when (element) {
            is LslExpressionFunctionCall -> {
                val functionName = element.functionName ?: return
                val kwdbData = KwdbData.getInstance(element.project)
                if (kwdbData.functions.containsKey(functionName)) {
                    val resolved = element.reference?.resolve()
                    if (resolved == null || kwdbData.hasElement(resolved) || resolved == kwdbData.functions[functionName]) {
                        val target = element.functionNameIdentifier ?: element
                        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(target)
                            .textAttributes(LslColorKeys.BUILTIN_FUNCTION)
                            .create()
                    }
                }
            }

            is LslLValue -> {
                val variableName = element.variableName ?: return
                val kwdbData = KwdbData.getInstance(element.project)
                if (kwdbData.constants.containsKey(variableName)) {
                    val resolved = element.reference?.resolve()
                    if (resolved == null || kwdbData.hasElement(resolved) || resolved == kwdbData.constants[variableName]) {
                        val target = element.variableNameIdentifier ?: element
                        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(target)
                            .textAttributes(LslColorKeys.BUILTIN_CONSTANT)
                            .create()
                    }
                }
            }

            is LslEvent -> {
                val eventName = element.name ?: return
                val kwdbData = KwdbData.getInstance(element.project)
                if (kwdbData.events.containsKey(eventName)) {
                    val target = element.nameIdentifier ?: element
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(target)
                        .textAttributes(LslColorKeys.EVENT)
                        .create()
                }
            }
        }
    }
}
