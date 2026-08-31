package io.github.koollsl.lsl.references

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import io.github.koollsl.lsl.KwdbData
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslExpressionFunctionCall
import io.github.koollsl.lsl.psi.LslFile
import io.github.koollsl.lsl.psi.LslFunction

class LslExpressionFunctionCallReference(val element: LslExpressionFunctionCall) :
    PsiReferenceBase<PsiElement>(element), PsiPolyVariantReference {

    private val engine: LslPreprocessorEngine =
        element.project.service<LslPreprocessorEngine>()

    override fun resolve(): PsiElement? =
        multiResolve(false).firstOrNull()?.element

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        if (engine.isElementDisabled(element)) {
            return arrayOf(PsiElementResolveResult(element))
        }

        return ResolveCache.getInstance(element.project).resolveWithCaching(
            this,
            { referenceBase, _ -> referenceBase.resolveInner() },
            false,
            incompleteCode,
        )
    }

    override fun getRangeInElement(): TextRange =
        element.functionNameIdentifier?.textRangeInParent ?: TextRange.EMPTY_RANGE

    private fun resolveInner(): Array<ResolveResult> {
        if (engine.isElementDisabled(element)) {
            return arrayOf(PsiElementResolveResult(element))
        }

        val functionName = element.functionName ?: return emptyArray()
        val project = element.project

        // 1. Local functions
        val localFunctions = element.containingFile.children
            .filterIsInstance<LslFunction>()
            .filter { it.name == functionName }

        // 2. Included files
        val includedFiles = engine.getIncludedFiles(element.containingFile as LslFile)
        val includedFunctions = includedFiles.flatMap { file ->
            (file as? LslFile)?.children
                ?.filterIsInstance<LslFunction>()
                ?.filter { it.name == functionName }
                ?: emptyList()
        }

        // 3. Workspace library files (.lslp / .lslm)
        val lslpVirtualFiles = listOf("lslp", "lslm").flatMap { ext ->
            FilenameIndex.getAllFilesByExt(project, ext, GlobalSearchScope.projectScope(project))
        }
        val lslpFunctions = lslpVirtualFiles.flatMap { virtualFile ->
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? LslFile
            psiFile?.children
                ?.filterIsInstance<LslFunction>()
                ?.filter { it.name == functionName }
                ?: emptyList()
        }

        // 4. Built‑in functions
        val builtinFunctions = listOfNotNull(
            KwdbData.getInstance(project).functions[functionName]
        )

        return (localFunctions + includedFunctions + lslpFunctions + builtinFunctions)
            .map { PsiElementResolveResult(it) }
            .toTypedArray()
    }
}
