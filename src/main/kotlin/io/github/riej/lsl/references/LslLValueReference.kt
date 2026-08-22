package io.github.riej.lsl.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import io.github.riej.lsl.KwdbData
import io.github.riej.lsl.preprocessor.LslPreprocessorEngine
import io.github.riej.lsl.psi.*

class LslLValueReference(val element: LslLValue) :
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
        element.variableNameIdentifier?.textRangeInParent ?: TextRange.EMPTY_RANGE

    private fun resolveInner(): Array<ResolveResult> {
        if (LslPreprocessorEngine.isElementDisabled(element)) {
            return arrayOf(PsiElementResolveResult(element))
        }
        val result = ArrayList<ResolveResult>()
        var node: PsiElement? = element
        while (node != null) {
            when (node) {
                is LslStatementBlock ->
                    result.addAll(
                        node.children.takeWhile { it != node }
                            .filterIsInstance<LslStatementVariable>()
                            .filter { it.name == element.variableName }
                            .let { ArrayList(it).asReversed() }
                            .map { PsiElementResolveResult(it) }
                    )

                is LslEvent ->
                    result.addAll(
                        node.arguments
                            .filter { it.name == element.variableName }
                            .map { PsiElementResolveResult(it) }
                    )

                is LslFunction ->
                    result.addAll(
                        node.arguments
                            .filter { it.name == element.variableName }
                            .map { PsiElementResolveResult(it) }
                    )

                is LslFile -> {
                    // 1. Local file global variables
                    val localGlobals = node.children
                        .filterIsInstance<LslGlobalVariable>()
                        .filter { it.name == element.variableName }
                        .let { ArrayList(it).asReversed() }
                        .map { PsiElementResolveResult(it) }

                    // 2. Global variables in included files (#include / //#include)
                    val includedFiles = LslPreprocessorEngine.getIncludedFiles(node)
                    val includedGlobals = includedFiles.flatMap { file ->
                        (file as? LslFile)?.children
                            ?.filterIsInstance<LslGlobalVariable>()
                            ?.filter { it.name == element.variableName }
                            ?.map { PsiElementResolveResult(it) } ?: emptyList()
                    }

                    // 3. Global variables declared inside .lslp / .lslm files across the project
                    val project = element.project
                    val lslpVirtualFiles = listOf("lslp", "lslm").flatMap { ext ->
                        FilenameIndex.getAllFilesByExt(project, ext, GlobalSearchScope.projectScope(project))
                    }
                    val lslpGlobals = lslpVirtualFiles.flatMap { virtualFile ->
                        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) as? LslFile
                        psiFile?.children
                            ?.filterIsInstance<LslGlobalVariable>()
                            ?.filter { it.name == element.variableName }
                            ?.map { PsiElementResolveResult(it) } ?: emptyList()
                    }

                    // 4. Built-in constants (e.g., TRUE, FALSE, AGENT, etc.)
                    val builtinConstants = listOfNotNull(
                        KwdbData.getInstance(project).constants[element.variableName]
                    ).map { PsiElementResolveResult(it) }

                    return (result + localGlobals + includedGlobals + lslpGlobals + builtinConstants).toTypedArray()
                }
            }

            node = node.parent
        }

        return result.toTypedArray()
    }
}