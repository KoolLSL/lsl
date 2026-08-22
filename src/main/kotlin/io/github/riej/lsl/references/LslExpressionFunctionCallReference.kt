package io.github.riej.lsl.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import io.github.riej.lsl.KwdbData
import io.github.riej.lsl.preprocessor.LslPreprocessorEngine
import io.github.riej.lsl.psi.LslExpressionFunctionCall
import io.github.riej.lsl.psi.LslFile
import io.github.riej.lsl.psi.LslFunction

class LslExpressionFunctionCallReference(val element: LslExpressionFunctionCall) :
    PsiReferenceBase<PsiElement>(element), PsiPolyVariantReference {
    override fun resolve(): PsiElement? =
        multiResolve(false).firstOrNull()?.element

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (LslPreprocessorEngine.isElementDisabled(element)) {
            return arrayOf(PsiElementResolveResult(element))
        }
        return ResolveCache.getInstance(element.project).resolveWithCaching(
            this,
            { referenceBase, _ -> referenceBase.resolveInner() },
            false, incompleteCode,
        )
    }

    override fun getRangeInElement(): TextRange =
        element.functionNameIdentifier?.textRangeInParent ?: TextRange.EMPTY_RANGE

    private fun resolveInner(): Array<ResolveResult> {
        if (LslPreprocessorEngine.isElementDisabled(element)) {
            return arrayOf(PsiElementResolveResult(element))
        }
        val functionName = element.functionName ?: return emptyArray()
        val project = element.project

        // 1. Find local functions in the current file
        val localFunctions = element
            .containingFile
            .children
            .filterIsInstance<LslFunction>()
            .filter { it.name == functionName }

        // 2. Find functions in included files (#include / //#include)
        val includedFiles = element.containingFile?.let { LslPreprocessorEngine.getIncludedFiles(it) } ?: emptySet()
        val includedFunctions = includedFiles.flatMap { file ->
            (file as? LslFile)?.children?.filterIsInstance<LslFunction>()?.filter { it.name == functionName } ?: emptyList()
        }

        // 3. Find functions in all .lslp / .lslm library files in the project workspace
        val lslpVirtualFiles = listOf("lslp", "lslm").flatMap { ext ->
            FilenameIndex.getAllFilesByExt(project, ext, GlobalSearchScope.projectScope(project))
        }
        val lslpFunctions = lslpVirtualFiles.flatMap { virtualFile ->
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? LslFile
            psiFile?.children?.filterIsInstance<LslFunction>()?.filter { it.name == functionName } ?: emptyList()
        }

        // 4. Find standard LSL built-in functions
        val builtinFunctions = listOfNotNull(KwdbData.getInstance(project).functions[functionName])

        // Combine all results into PsiElementResolveResult array
        return (localFunctions + includedFunctions + lslpFunctions + builtinFunctions)
            .map { PsiElementResolveResult(it) }
            .toTypedArray()
    }
}