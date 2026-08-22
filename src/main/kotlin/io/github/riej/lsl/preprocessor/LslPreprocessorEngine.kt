package io.github.riej.lsl.preprocessor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import io.github.riej.lsl.LslPrimitiveType
import io.github.riej.lsl.parser.LslTypes
import io.github.riej.lsl.psi.*
import io.github.riej.lsl.safeguards.LslBuildOutputNotificationProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

object LslPreprocessorEngine {

    private const val MAX_INCLUDE_DEPTH = 30
    private val DIRECTIVE_REGEX = Regex("""^\s*#\s*([a-zA-Z_]\w*)(?:\s+(.*)|$)""")

    fun isElementDisabled(element: PsiElement?): Boolean {
        if (element == null) return false
        val file = try {
            element.containingFile
        } catch (e: Exception) {
            null
        } ?: return false
        if (element is PsiFile) return false
        val elementRange = try {
            element.textRange
        } catch (e: Exception) {
            null
        } ?: return false
        val disabledRanges = try {
            getDisabledRanges(file)
        } catch (e: Exception) {
            emptyList()
        }
        if (disabledRanges.isEmpty()) return false

        return try {
            if (elementRange.isEmpty) {
                disabledRanges.any { it.containsOffset(element.textOffset) }
            } else {
                disabledRanges.any { it.contains(elementRange) || it.intersects(elementRange) }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getDisabledRanges(file: PsiFile?): List<TextRange> {
        if (file == null || !file.isValid) return emptyList()
        return try {
            CachedValuesManager.getCachedValue(file) {
                val ranges = try {
                    computeDisabledRanges(file)
                } catch (e: Exception) {
                    emptyList()
                }
                CachedValueProvider.Result.create(ranges, file)
            } ?: emptyList()
        } catch (e: Exception) {
            try {
                computeDisabledRanges(file)
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    fun computeDisabledRanges(file: PsiFile?): List<TextRange> {
        if (file == null || !file.isValid) return emptyList()
        val text = try {
            file.text
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val project = try {
            file.project
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        if (project.isDisposed) return emptyList()

        val definitions = mutableMapOf<String, String>()
        val visitedFiles = mutableSetOf<String>()
        file.virtualFile?.path?.let { visitedFiles.add(it) }
        file.virtualFile?.canonicalPath?.let { visitedFiles.add(it) }
        file.originalFile.virtualFile?.path?.let { visitedFiles.add(it) }
        file.originalFile.virtualFile?.canonicalPath?.let { visitedFiles.add(it) }
        file.name.let { visitedFiles.add(it) }

        val lines = getLines(text)
        val stack = ArrayDeque<BlockState>()
        val rawDisabledRanges = mutableListOf<TextRange>()

        for (line in lines) {
            val trimmed = line.text.trim()
            val match = DIRECTIVE_REGEX.find(trimmed)

            if (match != null) {
                val directive = match.groupValues.getOrNull(1)?.lowercase() ?: continue
                val rawArgs = match.groupValues.getOrNull(2) ?: ""
                val args = stripTrailingComment(rawArgs).trim()

                when (directive) {
                    "ifdef" -> {
                        val parentActive = isCurrentlyActive(stack)
                        val ident = args.split(Regex("""\s+""")).firstOrNull()?.trim() ?: ""
                        val cond = parentActive && ident.isNotEmpty() && definitions.containsKey(ident)
                        stack.addLast(BlockState(parentActive = parentActive, conditionMet = cond, currentBranchActive = cond))
                    }
                    "ifndef" -> {
                        val parentActive = isCurrentlyActive(stack)
                        val ident = args.split(Regex("""\s+""")).firstOrNull()?.trim() ?: ""
                        val cond = parentActive && (ident.isEmpty() || !definitions.containsKey(ident))
                        stack.addLast(BlockState(parentActive = parentActive, conditionMet = cond, currentBranchActive = cond))
                    }
                    "if" -> {
                        val parentActive = isCurrentlyActive(stack)
                        val cond = parentActive && evaluateCondition(args, definitions)
                        stack.addLast(BlockState(parentActive = parentActive, conditionMet = cond, currentBranchActive = cond))
                    }
                    "elif" -> {
                        if (stack.isNotEmpty()) {
                            val top = stack.last()
                            val cond = top.parentActive && !top.conditionMet && evaluateCondition(args, definitions)
                            top.currentBranchActive = cond
                            if (cond) top.conditionMet = true
                        }
                    }
                    "else" -> {
                        if (stack.isNotEmpty()) {
                            val top = stack.last()
                            val cond = top.parentActive && !top.conditionMet
                            top.currentBranchActive = cond
                            top.conditionMet = true
                        }
                    }
                    "endif" -> {
                        if (stack.isNotEmpty()) {
                            stack.removeLast()
                        }
                    }
                    "define" -> {
                        if (isCurrentlyActive(stack)) {
                            parseAndAddDefine(args, definitions)
                        }
                    }
                    "undef" -> {
                        if (isCurrentlyActive(stack)) {
                            val ident = args.split(Regex("""\s+""")).firstOrNull()?.trim() ?: ""
                            if (ident.isNotEmpty()) {
                                definitions.remove(ident)
                            }
                        }
                    }
                    "include" -> {
                        if (isCurrentlyActive(stack)) {
                            val includedPath = args.trim().trim('"', '<', '>')
                            processInclude(includedPath, project, file, definitions, visitedFiles, 0)
                        }
                    }
                }
            } else {
                if (!isCurrentlyActive(stack)) {
                    if (line.endOffset > line.startOffset) {
                        rawDisabledRanges.add(TextRange(line.startOffset, line.endOffset))
                    }
                }
            }
        }

        return mergeContiguousRanges(rawDisabledRanges)
    }

    fun parseAndAddDefine(args: String, definitions: MutableMap<String, String>) {
        var cleanArgs = args.trim()
        if (cleanArgs.isEmpty()) return
        if (cleanArgs.startsWith("#")) {
            cleanArgs = cleanArgs.removePrefix("#").trim()
        }
        if (cleanArgs.startsWith("define", ignoreCase = true)) {
            cleanArgs = cleanArgs.substring(6).trim()
        }
        if (cleanArgs.isEmpty()) return
        val equalIdx = cleanArgs.indexOf('=')
        if (equalIdx != -1) {
            val key = cleanArgs.substring(0, equalIdx).trim().substringBefore('(').trim()
            val value = cleanArgs.substring(equalIdx + 1).trim()
            if (key.isNotEmpty()) {
                definitions[key] = value
            }
        } else {
            val parts = cleanArgs.split(Regex("""\s+"""), limit = 2)
            val key = parts[0].trim().substringBefore('(').trim()
            val value = if (parts.size > 1) parts[1].trim() else "1"
            if (key.isNotEmpty()) {
                definitions[key] = value
            }
        }
    }

    fun evaluateCondition(expression: String, definitions: Map<String, String>): Boolean {
        var trimmed = expression.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.removePrefix("#").trim()
        }
        if (trimmed.startsWith("if", ignoreCase = true) && (trimmed.length == 2 || trimmed[2].isWhitespace() || trimmed[2] == '(' || trimmed[2] == '!')) {
            trimmed = trimmed.substring(2).trim()
        } else if (trimmed.startsWith("elif", ignoreCase = true) && (trimmed.length == 4 || trimmed[4].isWhitespace() || trimmed[4] == '(' || trimmed[4] == '!')) {
            trimmed = trimmed.substring(4).trim()
        } else if (trimmed.startsWith("ifdef", ignoreCase = true) && (trimmed.length == 5 || trimmed[5].isWhitespace() || trimmed[5] == '(')) {
            val ident = trimmed.substring(5).trim().removeSurrounding("(", ")").trim()
            return definitions.containsKey(ident)
        } else if (trimmed.startsWith("ifndef", ignoreCase = true) && (trimmed.length == 6 || trimmed[6].isWhitespace() || trimmed[6] == '(')) {
            val ident = trimmed.substring(6).trim().removeSurrounding("(", ")").trim()
            return !definitions.containsKey(ident)
        }
        if (trimmed.isEmpty()) return false
        val tokens = tokenize(trimmed)
        if (tokens.isEmpty()) return false
        val parser = ExpressionParser(tokens, definitions)
        return try {
            parser.parse()
        } catch (e: Exception) {
            false
        }
    }

    fun getIncludedFiles(file: PsiFile?): Set<PsiFile> {
        if (file == null || !file.isValid) return emptySet()
        return try {
            CachedValuesManager.getCachedValue(file) {
                val result = mutableSetOf<PsiFile>()
                val visited = mutableSetOf<String>()
                file.virtualFile?.path?.let { visited.add(it) }
                file.virtualFile?.canonicalPath?.let { visited.add(it) }
                file.originalFile.virtualFile?.path?.let { visited.add(it) }
                file.originalFile.virtualFile?.canonicalPath?.let { visited.add(it) }
                visited.add(file.name)
                try {
                    collectIncludedFiles(file, file.project, result, visited, 0)
                } catch (e: Exception) {
                    // safe fallback
                }
                CachedValueProvider.Result.create(result, file)
            } ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun buildPreprocessedFile(virtualFile: VirtualFile, project: Project): VirtualFile? {
        return processLslpFile(virtualFile, project)
    }

    fun processLslpFile(virtualFile: VirtualFile, project: Project): VirtualFile? {
        if (project.isDisposed || !virtualFile.isValid) return null
        if (virtualFile.extension?.lowercase() != "lslp") return null
        if (LslBuildOutputNotificationProvider.isGeneratedBuildFile(virtualFile)) return null

        val parentDir = virtualFile.parent ?: return null
        val fileNameWithoutExtension = virtualFile.nameWithoutExtension
        val outputFileName = "$fileNameWithoutExtension.lsl"
        val isLocal = virtualFile.fileSystem is com.intellij.openapi.vfs.LocalFileSystem && !ApplicationManager.getApplication().isUnitTestMode

        if (isLocal) {
            try {
                val sourceParentFile = File(parentDir.path).canonicalFile
                val buildDir = File(sourceParentFile, "build").canonicalFile
                if (!buildDir.exists()) {
                    buildDir.mkdirs()
                }
                purgeNonLslFiles(buildDir, parentDir)
            } catch (t: Throwable) {
                // safe fallback
            }
        } else {
            purgeNonLslVirtualFiles(parentDir)
        }

        // 1. Run 3-pass preprocessing pipeline (Pass 1: Directives & Expansion, Pass 2: AST & DCE, Pass 3: Comment Retention & Output Generation)
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
        val finalizedCode = generatePreprocessedOutput(psiFile)

        // 2. Save the final cleaned .lsl file in VFS.
        var resultFile: VirtualFile? = null
        ApplicationManager.getApplication().runWriteAction {
            try {
                val buildVDir = parentDir.findChild("build")
                    ?: parentDir.createChildDirectory(this, "build")
                val targetFile = buildVDir.findChild(outputFileName)
                    ?: buildVDir.createChildData(this, outputFileName)
                VfsUtil.saveText(targetFile, finalizedCode)
                resultFile = targetFile
            } catch (t: Throwable) {
                // safe fallback
            }
        }

        if (isLocal) {
            try {
                val sourceParentFile = File(parentDir.path).canonicalFile
                val buildDir = File(sourceParentFile, "build").canonicalFile
                val outputFile = File(buildDir, outputFileName).canonicalFile
                outputFile.writeText(finalizedCode)
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputFile)?.refresh(false, false)
            } catch (t: Throwable) {
                // safe fallback
            }
        }

        return resultFile
    }

    fun purgeNonLslFiles(buildDir: File, parentVirtualDir: VirtualFile? = null) {
        try {
            if (buildDir.exists() && buildDir.isDirectory) {
                buildDir.listFiles()?.forEach { file ->
                    val ext = file.extension.lowercase()
                    if (ext != "lsl") {
                        try {
                            file.delete()
                        } catch (t: Throwable) {
                            // safe fallback
                        }
                    }
                }
            }
            if (parentVirtualDir != null) {
                purgeNonLslVirtualFiles(parentVirtualDir)
            }
        } catch (t: Throwable) {
            // safe fallback
        }
    }

    fun purgeNonLslVirtualFiles(parentVirtualDir: VirtualFile) {
        try {
            val buildVDir = parentVirtualDir.findChild("build")
            buildVDir?.children?.forEach { child ->
                val ext = child.extension?.lowercase()
                if (ext != "lsl") {
                    try {
                        ApplicationManager.getApplication().runWriteAction {
                            child.delete(this)
                        }
                    } catch (t: Throwable) {
                        // safe fallback
                    }
                }
            }
        } catch (t: Throwable) {
            // safe fallback
        }
    }

    fun processFileOnSave(virtualFile: VirtualFile, project: Project) {
        if (project.isDisposed || !virtualFile.isValid) return
        val ext = virtualFile.extension?.lowercase() ?: return
        if (ext == "lslp") {
            processLslpFile(virtualFile, project)
        } else if (ext == "lslm") {
            val lslpFiles = FilenameIndex.getAllFilesByExt(project, "lslp", GlobalSearchScope.projectScope(project))
            for (lslpFile in lslpFiles) {
                processLslpFile(lslpFile, project)
            }
        }
    }

    private val INC_START_REGEX = Regex("""^//\s*__LSL_INC_START__:(\d+):(.+)$""")
    private val INC_END_REGEX = Regex("""^//\s*__LSL_INC_END__:(\d+):(.+)$""")

    private sealed class PreprocessedItem {
        data class FloatingComment(val text: String) : PreprocessedItem()
        data class Declaration(
            val psiElement: PsiElement,
            val docComments: List<String>,
            val isSurviving: Boolean
        ) : PreprocessedItem()
        data class IncludeSection(
            val id: Int,
            val fileName: String,
            val items: MutableList<PreprocessedItem> = mutableListOf()
        ) : PreprocessedItem()
    }

    fun preprocess(file: PsiFile, initialDefinitions: Map<String, String> = emptyMap()): String {
        return generatePreprocessedOutput(file, initialDefinitions)
    }

    fun preprocessFile(file: PsiFile, initialDefinitions: Map<String, String> = emptyMap()): String {
        return generatePreprocessedOutput(file, initialDefinitions)
    }

    fun generatePreprocessedOutput(file: PsiFile, initialDefinitions: Map<String, String> = emptyMap()): String {
        val project = file.project
        if (project.isDisposed || !file.isValid) return ""

        // PASS 1 (Directives & Expansion):
        // Process all conditional compilation directives (#if, #ifdef, #ifndef, #elif, #else, #endif)
        // and evaluate macro expansions FIRST. Discard inactive code blocks before building the final AST.
        val definitions = initialDefinitions.toMutableMap()
        val visitedFiles = mutableSetOf<String>()
        val includeCounter = AtomicInteger(0)
        val expandedCode = expandDirectives(
            file = file,
            definitions = definitions,
            visitedFiles = visitedFiles,
            depth = 0,
            markIncludes = true,
            includeCounter = includeCounter
        )

        // PASS 2 (Inlining & AST & DCE):
        // Run inlining transformation for #inline functions and global variables
        val inliningResult = performInlining(expandedCode, project)
        val inlinedCode = inliningResult.transformedCode
        val inlinedGlobals = inliningResult.inlinedGlobals
        val inlinedFunctions = inliningResult.inlinedFunctions

        // Constant Propagation & Constant Folding Pass
        val optimizedCode = performConstantFoldingAndPropagation(inlinedCode, project)

        // Perform Dead Code Elimination (DCE) on the surviving active AST tree.
        val psiFile = LslElementFactory.createFile(project, optimizedCode)

        val allFunctions = psiFile.children.filterIsInstance<LslFunction>()
        val allGlobals = psiFile.children.filterIsInstance<LslGlobalVariable>()
        val allStates = psiFile.children.filterIsInstance<LslState>()

        val functionsByName = allFunctions.filter { it.name != null }.groupBy { it.name!! }
        val globalsByName = allGlobals.filter { it.name != null }.groupBy { it.name!! }
        val statesByName = allStates.filter { it.name != null }.groupBy { it.name!! }

        val visitedStates = mutableSetOf<LslState>()
        val visitedFunctions = mutableSetOf<LslFunction>()
        val visitedGlobals = mutableSetOf<LslGlobalVariable>()
        val queue = ArrayDeque<PsiElement>()

        for (state in allStates) {
            visitedStates.add(state)
            for (event in state.events) {
                queue.add(event)
            }
        }

        while (queue.isNotEmpty()) {
            val element = queue.removeFirst()

            val calls = PsiTreeUtil.collectElementsOfType(element, LslExpressionFunctionCall::class.java)
            for (call in calls) {
                val name = call.functionName ?: continue
                val funcs = functionsByName[name].orEmpty()
                for (func in funcs) {
                    if (visitedFunctions.add(func)) {
                        queue.add(func)
                    }
                }
            }

            val lValues = PsiTreeUtil.collectElementsOfType(element, LslLValue::class.java)
            for (lValue in lValues) {
                val varName = lValue.variableName ?: continue
                val globals = globalsByName[varName].orEmpty()
                if (globals.isEmpty()) continue

                if (!isShadowedLocally(lValue, varName)) {
                    for (globalVar in globals) {
                        if (visitedGlobals.add(globalVar)) {
                            queue.add(globalVar)
                        }
                    }
                }
            }

            val stateTransitions = PsiTreeUtil.collectElementsOfType(element, LslStatementState::class.java)
            for (stateTransition in stateTransitions) {
                val targetStateName = stateTransition.stateName ?: continue
                val targetStates = statesByName[targetStateName].orEmpty()
                for (targetState in targetStates) {
                    if (visitedStates.add(targetState)) {
                        for (event in targetState.events) {
                            queue.add(event)
                        }
                    }
                }
            }
        }

        // PASS 3 (Comment Retention & Output Generation):
        val mainFileName = file.name
        val rootSection = PreprocessedItem.IncludeSection(-1, mainFileName)
        val scopeStack = mutableListOf(rootSection)
        val pendingComments = mutableListOf<String>()
        var consecutiveNewlines = 0

        var curr = psiFile.firstChild
        while (curr != null) {
            val element = curr

            if (element is com.intellij.psi.PsiWhiteSpace) {
                consecutiveNewlines += element.text.count { it == '\n' }
                if (consecutiveNewlines > 1 && pendingComments.isNotEmpty()) {
                    val currentScope = scopeStack.last()
                    for (c in pendingComments) {
                        if (!isInlineDirectiveComment(c)) {
                            currentScope.items.add(PreprocessedItem.FloatingComment(c))
                        }
                    }
                    pendingComments.clear()
                }
                curr = curr.nextSibling
                continue
            }

            if (element is com.intellij.psi.PsiComment) {
                val text = element.text.trim()
                val startMatch = INC_START_REGEX.find(text)
                val endMatch = INC_END_REGEX.find(text)

                if (startMatch != null) {
                    val currentScope = scopeStack.last()
                    for (c in pendingComments) {
                        if (!isInlineDirectiveComment(c)) {
                            currentScope.items.add(PreprocessedItem.FloatingComment(c))
                        }
                    }
                    pendingComments.clear()
                    consecutiveNewlines = 0

                    val id = startMatch.groupValues[1].toInt()
                    val fileName = startMatch.groupValues[2].trim()
                    val newSection = PreprocessedItem.IncludeSection(id, fileName)
                    currentScope.items.add(newSection)
                    scopeStack.add(newSection)
                } else if (endMatch != null) {
                    val currentScope = scopeStack.last()
                    for (c in pendingComments) {
                        if (!isInlineDirectiveComment(c)) {
                            currentScope.items.add(PreprocessedItem.FloatingComment(c))
                        }
                    }
                    pendingComments.clear()
                    consecutiveNewlines = 0

                    if (scopeStack.size > 1) {
                        scopeStack.removeAt(scopeStack.size - 1)
                    }
                } else {
                    pendingComments.add(element.text)
                    consecutiveNewlines = 0
                }
                curr = curr.nextSibling
                continue
            }

            if (element is LslGlobalVariable || element is LslFunction || element is LslState) {
                val currentScope = scopeStack.last()
                val docComments = pendingComments.filter { !isInlineDirectiveComment(it) }
                pendingComments.clear()
                consecutiveNewlines = 0

                val isSurviving = when (element) {
                    is LslFunction -> element in visitedFunctions && element.name !in inlinedFunctions
                    is LslGlobalVariable -> element in visitedGlobals && element.name !in inlinedGlobals
                    is LslState -> element in visitedStates
                    else -> true
                }

                currentScope.items.add(
                    PreprocessedItem.Declaration(
                        psiElement = element,
                        docComments = docComments,
                        isSurviving = isSurviving
                    )
                )
                curr = curr.nextSibling
                continue
            }

            consecutiveNewlines = 0
            curr = curr.nextSibling
        }

        // Flush any remaining comments
        val currentScope = scopeStack.last()
        for (c in pendingComments) {
            if (!isInlineDirectiveComment(c)) {
                currentScope.items.add(PreprocessedItem.FloatingComment(c))
            }
        }
        pendingComments.clear()

        // Emitter
        val sb = StringBuilder()

        // 2. Top-of-File Build Banner:
        //    At line 1 of the generated output file, insert a header banner:
        //    // Generated by LSL Preprocessor on YYYY-MM-DD HH:mm:ss
        //    // Primary Source: <main_filename.lslp>
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        sb.append("// Generated by LSL Preprocessor on $timestamp\n")
        sb.append("// Primary Source: $mainFileName\n\n")

        fun hasDirectSurvivingDeclarations(section: PreprocessedItem.IncludeSection): Boolean {
            return section.items.any { item ->
                when (item) {
                    is PreprocessedItem.Declaration -> item.isSurviving
                    else -> false
                }
            }
        }

        fun hasAnySurvivingContent(section: PreprocessedItem.IncludeSection): Boolean {
            return section.items.any { item ->
                when (item) {
                    is PreprocessedItem.Declaration -> item.isSurviving
                    is PreprocessedItem.IncludeSection -> hasAnySurvivingContent(item)
                    else -> false
                }
            }
        }

        fun emitSection(section: PreprocessedItem.IncludeSection, isRoot: Boolean) {
            if (isRoot) {
                for (item in section.items) {
                    when (item) {
                        is PreprocessedItem.FloatingComment -> {
                            sb.append(item.text).append("\n\n")
                        }
                        is PreprocessedItem.Declaration -> {
                            if (item.isSurviving) {
                                for (doc in item.docComments) {
                                    sb.append(doc).append("\n")
                                }
                                sb.append(item.psiElement.text).append("\n\n")
                            }
                        }
                        is PreprocessedItem.IncludeSection -> {
                            emitSection(item, isRoot = false)
                        }
                    }
                }
            } else {
                val hasDirect = hasDirectSurvivingDeclarations(section)
                val hasAny = hasAnySurvivingContent(section)

                if (!hasAny) {
                    return
                }

                if (hasDirect) {
                    sb.append("// --- Begin Include: ${section.fileName} ---\n")
                    for (item in section.items) {
                        when (item) {
                            is PreprocessedItem.FloatingComment -> {
                                // Discard top-level floating comments from included files
                            }
                            is PreprocessedItem.Declaration -> {
                                if (item.isSurviving) {
                                    for (doc in item.docComments) {
                                        sb.append(doc).append("\n")
                                    }
                                    sb.append(item.psiElement.text).append("\n\n")
                                }
                            }
                            is PreprocessedItem.IncludeSection -> {
                                emitSection(item, isRoot = false)
                            }
                        }
                    }
                    val current = sb.toString().trimEnd()
                    sb.setLength(0)
                    sb.append(current).append("\n")
                    sb.append("// --- End Include: ${section.fileName} ---\n\n")
                } else {
                    for (item in section.items) {
                        if (item is PreprocessedItem.IncludeSection) {
                            emitSection(item, isRoot = false)
                        }
                    }
                }
            }
        }

        emitSection(rootSection, isRoot = true)

        val resultText = sb.toString()
        val cleaned = resultText.replace(Regex("(\\r?\\n){3,}"), "\n\n").trimEnd() + "\n"
        return cleaned
    }

    fun expandDirectives(
        file: PsiFile,
        definitions: MutableMap<String, String> = mutableMapOf(),
        visitedFiles: MutableSet<String> = mutableSetOf(),
        depth: Int = 0,
        markIncludes: Boolean = false,
        includeCounter: AtomicInteger = AtomicInteger(0)
    ): String {
        if (depth > MAX_INCLUDE_DEPTH || !file.isValid || file.project.isDisposed) return ""
        val text = try {
            file.text
        } catch (e: Exception) {
            null
        } ?: return ""

        file.virtualFile?.path?.let { visitedFiles.add(it) }
        file.virtualFile?.canonicalPath?.let { visitedFiles.add(it) }
        file.originalFile.virtualFile?.path?.let { visitedFiles.add(it) }
        file.originalFile.virtualFile?.canonicalPath?.let { visitedFiles.add(it) }
        file.name.let { visitedFiles.add(it) }

        val lines = getLines(text)
        val stack = ArrayDeque<BlockState>()
        val result = StringBuilder()

        for (line in lines) {
            val trimmed = line.text.trim()
            val match = DIRECTIVE_REGEX.find(trimmed)

            if (match != null) {
                val directive = match.groupValues.getOrNull(1)?.lowercase() ?: continue
                val rawArgs = match.groupValues.getOrNull(2) ?: ""
                val args = stripTrailingComment(rawArgs).trim()

                when (directive) {
                    "ifdef" -> {
                        val parentActive = isCurrentlyActive(stack)
                        val ident = args.split(Regex("""\s+""")).firstOrNull()?.trim() ?: ""
                        val cond = parentActive && ident.isNotEmpty() && definitions.containsKey(ident)
                        stack.addLast(BlockState(parentActive = parentActive, conditionMet = cond, currentBranchActive = cond))
                    }
                    "ifndef" -> {
                        val parentActive = isCurrentlyActive(stack)
                        val ident = args.split(Regex("""\s+""")).firstOrNull()?.trim() ?: ""
                        val cond = parentActive && (ident.isEmpty() || !definitions.containsKey(ident))
                        stack.addLast(BlockState(parentActive = parentActive, conditionMet = cond, currentBranchActive = cond))
                    }
                    "if" -> {
                        val parentActive = isCurrentlyActive(stack)
                        val cond = parentActive && evaluateCondition(args, definitions)
                        stack.addLast(BlockState(parentActive = parentActive, conditionMet = cond, currentBranchActive = cond))
                    }
                    "elif" -> {
                        if (stack.isNotEmpty()) {
                            val top = stack.last()
                            val cond = top.parentActive && !top.conditionMet && evaluateCondition(args, definitions)
                            top.currentBranchActive = cond
                            if (cond) top.conditionMet = true
                        }
                    }
                    "else" -> {
                        if (stack.isNotEmpty()) {
                            val top = stack.last()
                            val cond = top.parentActive && !top.conditionMet
                            top.currentBranchActive = cond
                            top.conditionMet = true
                        }
                    }
                    "endif" -> {
                        if (stack.isNotEmpty()) {
                            stack.removeLast()
                        }
                    }
                    "define" -> {
                        if (isCurrentlyActive(stack)) {
                            parseAndAddDefine(args, definitions)
                        }
                    }
                    "undef" -> {
                        if (isCurrentlyActive(stack)) {
                            val ident = args.split(Regex("""\s+""")).firstOrNull()?.trim() ?: ""
                            if (ident.isNotEmpty()) {
                                definitions.remove(ident)
                            }
                        }
                    }
                    "inline" -> {
                        if (isCurrentlyActive(stack)) {
                            result.append("// __LSL_INLINE__\n")
                            if (rawArgs.trim().isNotEmpty()) {
                                result.append(rawArgs.trim()).append("\n")
                            }
                        }
                    }
                    "include" -> {
                        if (isCurrentlyActive(stack)) {
                            val includedPath = args.trim().trim('"', '<', '>')
                            val includedPsi = resolveIncludeFile(includedPath, file, file.project, visitedFiles)
                            if (includedPsi != null && includedPsi.isValid) {
                                if (markIncludes) {
                                    val id = includeCounter.incrementAndGet()
                                    val incName = includedPsi.name
                                    result.append("// __LSL_INC_START__:$id:$incName\n")
                                    val includedCode = expandDirectives(
                                        file = includedPsi,
                                        definitions = definitions,
                                        visitedFiles = visitedFiles,
                                        depth = depth + 1,
                                        markIncludes = true,
                                        includeCounter = includeCounter
                                    )
                                    if (includedCode.isNotEmpty()) {
                                        result.append(includedCode).append("\n")
                                    }
                                    result.append("// __LSL_INC_END__:$id:$incName\n")
                                } else {
                                    val includedCode = expandDirectives(
                                        file = includedPsi,
                                        definitions = definitions,
                                        visitedFiles = visitedFiles,
                                        depth = depth + 1,
                                        markIncludes = false,
                                        includeCounter = includeCounter
                                    )
                                    if (includedCode.isNotEmpty()) {
                                        result.append(includedCode).append("\n")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (isCurrentlyActive(stack)) {
                    result.append(line.text).append("\n")
                }
            }
        }
        return result.toString()
    }

    fun resolveIncludeFile(
        includedPath: String,
        containingFile: PsiFile?,
        project: Project,
        visitedFiles: Set<String> = emptySet()
    ): PsiFile? {
        if (project.isDisposed || includedPath.isEmpty()) return null
        val cleanName = includedPath.substringAfterLast('/').substringAfterLast('\\')
        val parent = containingFile?.virtualFile?.parent
        val projectRoot = try {
            if (project.basePath != null && containingFile?.virtualFile?.isInLocalFileSystem == true) {
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(project.basePath!!)
            } else null
        } catch (e: Exception) {
            null
        }

        val virtualFile = try {
            parent?.findFileByRelativePath(includedPath)
                ?: parent?.findFileByRelativePath("$includedPath.lslp")
                ?: parent?.findFileByRelativePath("$includedPath.lslm")
                ?: parent?.findFileByRelativePath("$includedPath.lsl")
                ?: projectRoot?.findFileByRelativePath(includedPath)
                ?: projectRoot?.findFileByRelativePath("$includedPath.lslp")
                ?: projectRoot?.findFileByRelativePath("$includedPath.lslm")
                ?: projectRoot?.findFileByRelativePath("$includedPath.lsl")
                ?: FilenameIndex.getFilesByName(project, cleanName, GlobalSearchScope.allScope(project)).firstOrNull()?.virtualFile
                ?: FilenameIndex.getFilesByName(project, "$cleanName.lslp", GlobalSearchScope.allScope(project)).firstOrNull()?.virtualFile
                ?: FilenameIndex.getFilesByName(project, "$cleanName.lslm", GlobalSearchScope.allScope(project)).firstOrNull()?.virtualFile
                ?: FilenameIndex.getFilesByName(project, "$cleanName.lsl", GlobalSearchScope.allScope(project)).firstOrNull()?.virtualFile
        } catch (e: Exception) {
            null
        } ?: return null

        val canonical = virtualFile.canonicalPath ?: virtualFile.path
        if (visitedFiles.contains(canonical) || visitedFiles.contains(virtualFile.path) || visitedFiles.contains(virtualFile.name)) {
            return null
        }

        return try {
            PsiManager.getInstance(project).findFile(virtualFile)
        } catch (e: Exception) {
            null
        }
    }

    fun eliminateDeadCode(project: Project, code: String): String {
        val trimmedCode = code.trim()
        if (trimmedCode.isEmpty() || project.isDisposed) return ""
        val psiFile = try {
            LslElementFactory.createFile(project, code)
        } catch (e: Exception) {
            return code
        }

        val allFunctions = psiFile.children.filterIsInstance<LslFunction>()
        val allGlobals = psiFile.children.filterIsInstance<LslGlobalVariable>()
        val allStates = psiFile.children.filterIsInstance<LslState>()

        val functionsByName = allFunctions.filter { it.name != null }.groupBy { it.name!! }
        val globalsByName = allGlobals.filter { it.name != null }.groupBy { it.name!! }
        val statesByName = allStates.filter { it.name != null }.groupBy { it.name!! }

        val visitedStates = mutableSetOf<LslState>()
        val visitedFunctions = mutableSetOf<LslFunction>()
        val visitedGlobals = mutableSetOf<LslGlobalVariable>()
        val queue = ArrayDeque<PsiElement>()

        // 2a. Identify all event handlers (state_entry, touch_start, timer, link_message, etc.) across all states as root entry points.
        for (state in allStates) {
            visitedStates.add(state)
            for (event in state.events) {
                queue.add(event)
            }
        }

        while (queue.isNotEmpty()) {
            val element = queue.removeFirst()

            // 1. Function calls
            val calls = PsiTreeUtil.collectElementsOfType(element, LslExpressionFunctionCall::class.java)
            for (call in calls) {
                val name = call.functionName ?: continue
                val funcs = functionsByName[name].orEmpty()
                for (func in funcs) {
                    if (visitedFunctions.add(func)) {
                        queue.add(func)
                    }
                }
            }

            // 2. Global variable references
            val lValues = PsiTreeUtil.collectElementsOfType(element, LslLValue::class.java)
            for (lValue in lValues) {
                val varName = lValue.variableName ?: continue
                val globals = globalsByName[varName].orEmpty()
                if (globals.isEmpty()) continue

                if (!isShadowedLocally(lValue, varName)) {
                    for (globalVar in globals) {
                        if (visitedGlobals.add(globalVar)) {
                            queue.add(globalVar)
                        }
                    }
                }
            }

            // 3. State transitions (state <name>;)
            val stateTransitions = PsiTreeUtil.collectElementsOfType(element, LslStatementState::class.java)
            for (stateTransition in stateTransitions) {
                val targetStateName = stateTransition.stateName ?: continue
                val targetStates = statesByName[targetStateName].orEmpty()
                for (targetState in targetStates) {
                    if (visitedStates.add(targetState)) {
                        for (event in targetState.events) {
                            queue.add(event)
                        }
                    }
                }
            }
        }

        // 2c. Strip any top-level global function or global variable that is NOT in the REACHABLE set.
        val deadElements = mutableListOf<PsiElement>()
        for (func in allFunctions) {
            if (func !in visitedFunctions) {
                deadElements.add(func)
            }
        }
        for (global in allGlobals) {
            if (global !in visitedGlobals) {
                deadElements.add(global)
            }
        }

        if (deadElements.isEmpty()) {
            return code
        }

        // Sort dead elements in descending order of their start offset to perform safe text range deletions
        val rangesToDelete = deadElements
            .map { it.textRange }
            .sortedByDescending { it.startOffset }

        val sb = StringBuilder(code)
        for (range in rangesToDelete) {
            val (adjStart, adjEnd) = adjustRangeToDelete(sb.toString(), range.startOffset, range.endOffset)
            if (adjStart >= 0 && adjEnd <= sb.length && adjStart <= adjEnd) {
                sb.delete(adjStart, adjEnd)
            }
        }

        val resultText = sb.toString()
        val cleaned = resultText.replace(Regex("(\\r?\\n){3,}"), "\n\n")
        return cleaned.trim()
    }

    private fun adjustRangeToDelete(text: String, startOffset: Int, endOffset: Int): Pair<Int, Int> {
        var start = startOffset
        var end = endOffset

        // Check if everything backwards on the line before start is whitespace
        var lineStart = start
        while (lineStart > 0 && text[lineStart - 1] != '\n' && text[lineStart - 1] != '\r') {
            if (!text[lineStart - 1].isWhitespace()) {
                lineStart = -1
                break
            }
            lineStart--
        }
        if (lineStart >= 0) {
            start = lineStart
        }

        // Check trailing whitespace and newline
        var lineEnd = end
        while (lineEnd < text.length && (text[lineEnd] == ' ' || text[lineEnd] == '\t')) {
            lineEnd++
        }
        if (lineEnd < text.length && text[lineEnd] == '\r') {
            lineEnd++
        }
        if (lineEnd < text.length && text[lineEnd] == '\n') {
            lineEnd++
            end = lineEnd
        } else if (lineEnd == text.length) {
            end = lineEnd
        }

        return Pair(start, end)
    }

    data class InliningResult(
        val transformedCode: String,
        val inlinedGlobals: Set<String>,
        val inlinedFunctions: Set<String>
    )

    private fun isInlineDirectiveComment(commentText: String): Boolean {
        val trimmed = commentText.trim()
        return trimmed.contains("__LSL_INLINE__") || trimmed.matches(Regex("""^#\s*inline(\s.*)?$"""))
    }

    fun inlineCode(project: Project, code: String): String {
        if (project.isDisposed || code.isBlank()) return code
        val inliningResult = performInlining(code, project)
        val inlinedGlobals = inliningResult.inlinedGlobals
        val inlinedFunctions = inliningResult.inlinedFunctions
        val transformed = inliningResult.transformedCode

        if (inlinedGlobals.isEmpty() && inlinedFunctions.isEmpty()) return transformed

        val psiFile = try {
            LslElementFactory.createFile(project, transformed)
        } catch (e: Exception) {
            return transformed
        }

        val toDelete = mutableListOf<PsiElement>()
        for (child in psiFile.children) {
            if (child is LslGlobalVariable && child.name in inlinedGlobals) {
                toDelete.add(child)
            } else if (child is LslFunction && child.name in inlinedFunctions) {
                toDelete.add(child)
            }
        }

        if (toDelete.isEmpty()) return transformed
        val ranges = toDelete.map { it.textRange }.sortedByDescending { it.startOffset }
        val sb = StringBuilder(transformed)
        for (range in ranges) {
            val (adjStart, adjEnd) = adjustRangeToDelete(sb.toString(), range.startOffset, range.endOffset)
            if (adjStart in 0..adjEnd && adjEnd <= sb.length) {
                sb.delete(adjStart, adjEnd)
            }
        }
        return sb.toString().replace(Regex("(\\r?\\n){3,}"), "\n\n").trim()
    }

    sealed class ConstantValue {
        data class IntVal(val value: Int) : ConstantValue() {
            override fun toLiteralString(): String = value.toString()
        }
        data class FloatVal(val value: Float) : ConstantValue() {
            override fun toLiteralString(): String {
                val s = value.toString()
                return if (s.contains('.') || s.contains('e') || s.contains('E')) s else "$s.0"
            }
        }
        data class StrVal(val value: String) : ConstantValue() {
            override fun toLiteralString(): String {
                val escaped = value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\t", "\\t")
                return "\"$escaped\""
            }
        }

        abstract fun toLiteralString(): String
    }

    private fun unescapeLslString(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    else -> { sb.append(c); sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    fun evaluateConstantExpression(
        expr: PsiElement?,
        resolvedConstants: Map<String, ConstantValue> = emptyMap()
    ): ConstantValue? {
        if (expr == null) return null
        return when (expr) {
            is LslConstant -> {
                when {
                    expr.integerValue != null -> {
                        val text = expr.integerValue!!.text.trim()
                        val v = if (text.startsWith("0x", ignoreCase = true) || text.startsWith("0X")) {
                            text.substring(2).toLongOrNull(16)?.toInt()
                        } else {
                            text.toLongOrNull()?.toInt()
                        }
                        v?.let { ConstantValue.IntVal(it) }
                    }
                    expr.floatValue != null -> {
                        val text = expr.floatValue!!.text.trim()
                        val v = text.toFloatOrNull()
                        v?.let { ConstantValue.FloatVal(it) }
                    }
                    expr.stringValue != null -> {
                        val text = expr.stringValue!!.text
                        val unquoted = if (text.startsWith("\"") && text.endsWith("\"") && text.length >= 2) {
                            text.substring(1, text.length - 1)
                        } else if (text.startsWith("\"")) {
                            text.substring(1)
                        } else {
                            text
                        }
                        ConstantValue.StrVal(unescapeLslString(unquoted))
                    }
                    else -> null
                }
            }
            is LslExpressionParentheses -> {
                evaluateConstantExpression(expr.expression, resolvedConstants)
            }
            is LslExpressionTypeCast -> {
                val inner = evaluateConstantExpression(expr.expression, resolvedConstants) ?: return null
                when (expr.lslType) {
                    LslPrimitiveType.INTEGER -> when (inner) {
                        is ConstantValue.IntVal -> inner
                        is ConstantValue.FloatVal -> ConstantValue.IntVal(inner.value.toInt())
                        is ConstantValue.StrVal -> inner.value.toIntOrNull()?.let { ConstantValue.IntVal(it) }
                    }
                    LslPrimitiveType.FLOAT -> when (inner) {
                        is ConstantValue.FloatVal -> inner
                        is ConstantValue.IntVal -> ConstantValue.FloatVal(inner.value.toFloat())
                        is ConstantValue.StrVal -> inner.value.toFloatOrNull()?.let { ConstantValue.FloatVal(it) }
                    }
                    LslPrimitiveType.STRING -> when (inner) {
                        is ConstantValue.StrVal -> inner
                        is ConstantValue.IntVal -> ConstantValue.StrVal(inner.value.toString())
                        is ConstantValue.FloatVal -> ConstantValue.StrVal(inner.value.toString())
                    }
                    else -> null
                }
            }
            is LslLValue -> {
                val varName = expr.variableName ?: return null
                if (expr.item != null) return null
                if (isShadowedLocally(expr, varName)) return null
                resolvedConstants[varName]
            }
            is LslExpressionUnaryPrefix -> {
                val inner = evaluateConstantExpression(expr.expression, resolvedConstants) ?: return null
                when (expr.operator) {
                    LslTypes.PLUS -> when (inner) {
                        is ConstantValue.IntVal -> inner
                        is ConstantValue.FloatVal -> inner
                        else -> null
                    }
                    LslTypes.MINUS -> when (inner) {
                        is ConstantValue.IntVal -> ConstantValue.IntVal(-inner.value)
                        is ConstantValue.FloatVal -> ConstantValue.FloatVal(-inner.value)
                        else -> null
                    }
                    LslTypes.BITWISE_NOT -> when (inner) {
                        is ConstantValue.IntVal -> ConstantValue.IntVal(inner.value.inv())
                        else -> null
                    }
                    LslTypes.BOOLEAN_NOT -> when (inner) {
                        is ConstantValue.IntVal -> ConstantValue.IntVal(if (inner.value == 0) 1 else 0)
                        else -> null
                    }
                    else -> null
                }
            }
            is LslExpressionBinary -> {
                val left = evaluateConstantExpression(expr.expressionLeft, resolvedConstants) ?: return null
                val right = evaluateConstantExpression(expr.expressionRight, resolvedConstants) ?: return null
                val op = expr.operator ?: return null
                when (op) {
                    LslTypes.BITWISE_AND -> {
                        if (left is ConstantValue.IntVal && right is ConstantValue.IntVal) {
                            ConstantValue.IntVal(left.value and right.value)
                        } else null
                    }
                    LslTypes.BITWISE_OR -> {
                        if (left is ConstantValue.IntVal && right is ConstantValue.IntVal) {
                            ConstantValue.IntVal(left.value or right.value)
                        } else null
                    }
                    LslTypes.BITWISE_XOR -> {
                        if (left is ConstantValue.IntVal && right is ConstantValue.IntVal) {
                            ConstantValue.IntVal(left.value xor right.value)
                        } else null
                    }
                    LslTypes.SHIFT_LEFT -> {
                        if (left is ConstantValue.IntVal && right is ConstantValue.IntVal) {
                            ConstantValue.IntVal(left.value shl right.value)
                        } else null
                    }
                    LslTypes.SHIFT_RIGHT -> {
                        if (left is ConstantValue.IntVal && right is ConstantValue.IntVal) {
                            ConstantValue.IntVal(left.value shr right.value)
                        } else null
                    }
                    LslTypes.PLUS -> {
                        when {
                            left is ConstantValue.IntVal && right is ConstantValue.IntVal -> ConstantValue.IntVal(left.value + right.value)
                            left is ConstantValue.IntVal && right is ConstantValue.FloatVal -> ConstantValue.FloatVal(left.value.toFloat() + right.value)
                            left is ConstantValue.FloatVal && right is ConstantValue.IntVal -> ConstantValue.FloatVal(left.value + right.value.toFloat())
                            left is ConstantValue.FloatVal && right is ConstantValue.FloatVal -> ConstantValue.FloatVal(left.value + right.value)
                            left is ConstantValue.StrVal && right is ConstantValue.StrVal -> ConstantValue.StrVal(left.value + right.value)
                            else -> null
                        }
                    }
                    LslTypes.MINUS -> {
                        when {
                            left is ConstantValue.IntVal && right is ConstantValue.IntVal -> ConstantValue.IntVal(left.value - right.value)
                            left is ConstantValue.IntVal && right is ConstantValue.FloatVal -> ConstantValue.FloatVal(left.value.toFloat() - right.value)
                            left is ConstantValue.FloatVal && right is ConstantValue.IntVal -> ConstantValue.FloatVal(left.value - right.value.toFloat())
                            left is ConstantValue.FloatVal && right is ConstantValue.FloatVal -> ConstantValue.FloatVal(left.value - right.value)
                            else -> null
                        }
                    }
                    LslTypes.MULTIPLE -> {
                        when {
                            left is ConstantValue.IntVal && right is ConstantValue.IntVal -> ConstantValue.IntVal(left.value * right.value)
                            left is ConstantValue.IntVal && right is ConstantValue.FloatVal -> ConstantValue.FloatVal(left.value.toFloat() * right.value)
                            left is ConstantValue.FloatVal && right is ConstantValue.IntVal -> ConstantValue.FloatVal(left.value * right.value.toFloat())
                            left is ConstantValue.FloatVal && right is ConstantValue.FloatVal -> ConstantValue.FloatVal(left.value * right.value)
                            else -> null
                        }
                    }
                    LslTypes.DIVIDE -> {
                        when {
                            left is ConstantValue.IntVal && right is ConstantValue.IntVal -> {
                                if (right.value == 0) null else ConstantValue.IntVal(left.value / right.value)
                            }
                            left is ConstantValue.IntVal && right is ConstantValue.FloatVal -> {
                                if (right.value == 0f) null else ConstantValue.FloatVal(left.value.toFloat() / right.value)
                            }
                            left is ConstantValue.FloatVal && right is ConstantValue.IntVal -> {
                                if (right.value == 0) null else ConstantValue.FloatVal(left.value / right.value.toFloat())
                            }
                            left is ConstantValue.FloatVal && right is ConstantValue.FloatVal -> {
                                if (right.value == 0f) null else ConstantValue.FloatVal(left.value / right.value)
                            }
                            else -> null
                        }
                    }
                    LslTypes.MODULUS -> {
                        if (left is ConstantValue.IntVal && right is ConstantValue.IntVal) {
                            if (right.value == 0) null else ConstantValue.IntVal(left.value % right.value)
                        } else null
                    }
                    LslTypes.EQUAL -> {
                        val eq = when {
                            left is ConstantValue.IntVal && right is ConstantValue.IntVal -> left.value == right.value
                            left is ConstantValue.FloatVal && right is ConstantValue.FloatVal -> left.value == right.value
                            left is ConstantValue.IntVal && right is ConstantValue.FloatVal -> left.value.toFloat() == right.value
                            left is ConstantValue.FloatVal && right is ConstantValue.IntVal -> left.value == right.value.toFloat()
                            left is ConstantValue.StrVal && right is ConstantValue.StrVal -> left.value == right.value
                            else -> false
                        }
                        ConstantValue.IntVal(if (eq) 1 else 0)
                    }
                    LslTypes.NOT_EQUAL -> {
                        val eq = when {
                            left is ConstantValue.IntVal && right is ConstantValue.IntVal -> left.value == right.value
                            left is ConstantValue.FloatVal && right is ConstantValue.FloatVal -> left.value == right.value
                            left is ConstantValue.IntVal && right is ConstantValue.FloatVal -> left.value.toFloat() == right.value
                            left is ConstantValue.FloatVal && right is ConstantValue.IntVal -> left.value == right.value.toFloat()
                            left is ConstantValue.StrVal && right is ConstantValue.StrVal -> left.value == right.value
                            else -> true
                        }
                        ConstantValue.IntVal(if (!eq) 1 else 0)
                    }
                    LslTypes.LESS -> {
                        val res = when {
                            left is ConstantValue.IntVal && right is ConstantValue.IntVal -> left.value < right.value
                            left is ConstantValue.FloatVal && right is ConstantValue.FloatVal -> left.value < right.value
                            left is ConstantValue.IntVal && right is ConstantValue.FloatVal -> left.value.toFloat() < right.value
                            left is ConstantValue.FloatVal && right is ConstantValue.IntVal -> left.value < right.value.toFloat()
                            else -> null
                        }
                        res?.let { ConstantValue.IntVal(if (it) 1 else 0) }
                    }
                    LslTypes.LESS_EQUAL -> {
                        val res = when {
                            left is ConstantValue.IntVal && right is ConstantValue.IntVal -> left.value <= right.value
                            left is ConstantValue.FloatVal && right is ConstantValue.FloatVal -> left.value <= right.value
                            left is ConstantValue.IntVal && right is ConstantValue.FloatVal -> left.value.toFloat() <= right.value
                            left is ConstantValue.FloatVal && right is ConstantValue.IntVal -> left.value <= right.value.toFloat()
                            else -> null
                        }
                        res?.let { ConstantValue.IntVal(if (it) 1 else 0) }
                    }
                    LslTypes.GREATER -> {
                        val res = when {
                            left is ConstantValue.IntVal && right is ConstantValue.IntVal -> left.value > right.value
                            left is ConstantValue.FloatVal && right is ConstantValue.FloatVal -> left.value > right.value
                            left is ConstantValue.IntVal && right is ConstantValue.FloatVal -> left.value.toFloat() > right.value
                            left is ConstantValue.FloatVal && right is ConstantValue.IntVal -> left.value > right.value.toFloat()
                            else -> null
                        }
                        res?.let { ConstantValue.IntVal(if (it) 1 else 0) }
                    }
                    LslTypes.GREATER_EQUAL -> {
                        val res = when {
                            left is ConstantValue.IntVal && right is ConstantValue.IntVal -> left.value >= right.value
                            left is ConstantValue.FloatVal && right is ConstantValue.FloatVal -> left.value >= right.value
                            left is ConstantValue.IntVal && right is ConstantValue.FloatVal -> left.value.toFloat() >= right.value
                            left is ConstantValue.FloatVal && right is ConstantValue.IntVal -> left.value >= right.value.toFloat()
                            else -> null
                        }
                        res?.let { ConstantValue.IntVal(if (it) 1 else 0) }
                    }
                    LslTypes.BOOLEAN_AND -> {
                        if (left is ConstantValue.IntVal && right is ConstantValue.IntVal) {
                            ConstantValue.IntVal(if (left.value != 0 && right.value != 0) 1 else 0)
                        } else null
                    }
                    LslTypes.BOOLEAN_OR -> {
                        if (left is ConstantValue.IntVal && right is ConstantValue.IntVal) {
                            ConstantValue.IntVal(if (left.value != 0 || right.value != 0) 1 else 0)
                        } else null
                    }
                    else -> null
                }
            }
            else -> null
        }
    }

    fun identifyConstantVariables(psiFile: PsiFile): Map<String, ConstantValue> {
        val allGlobals = psiFile.children.filterIsInstance<LslGlobalVariable>()
        val candidateGlobals = allGlobals.filter {
            it.name != null && it.expression != null && it.lslType in setOf(
                LslPrimitiveType.INTEGER,
                LslPrimitiveType.FLOAT,
                LslPrimitiveType.STRING
            )
        }
        if (candidateGlobals.isEmpty()) return emptyMap()

        val candidateNames = candidateGlobals.mapNotNull { it.name }.toSet()
        val mutatedVars = mutableSetOf<String>()

        // Check assignments
        val assignments = PsiTreeUtil.collectElementsOfType(psiFile, LslExpressionAssignment::class.java)
        for (assignment in assignments) {
            val varName = assignment.lValue?.variableName ?: continue
            if (varName in candidateNames && !isShadowedLocally(assignment.lValue!!, varName)) {
                mutatedVars.add(varName)
            }
        }

        // Check prefix ++/--
        val prefixes = PsiTreeUtil.collectElementsOfType(psiFile, LslExpressionUnaryPrefix::class.java)
        for (prefix in prefixes) {
            if (prefix.operator == LslTypes.PLUS_PLUS || prefix.operator == LslTypes.MINUS_MINUS) {
                val lVal = prefix.expression as? LslLValue ?: continue
                val varName = lVal.variableName ?: continue
                if (varName in candidateNames && !isShadowedLocally(lVal, varName)) {
                    mutatedVars.add(varName)
                }
            }
        }

        // Check postfix ++/--
        val postfixes = PsiTreeUtil.collectElementsOfType(psiFile, LslExpressionUnaryPostfix::class.java)
        for (postfix in postfixes) {
            if (postfix.operator == LslTypes.PLUS_PLUS || postfix.operator == LslTypes.MINUS_MINUS) {
                val lVal = postfix.expression as? LslLValue ?: continue
                val varName = lVal.variableName ?: continue
                if (varName in candidateNames && !isShadowedLocally(lVal, varName)) {
                    mutatedVars.add(varName)
                }
            }
        }

        val validCandidates = candidateGlobals.filter { it.name !in mutatedVars }.associateBy { it.name!! }
        val resolved = mutableMapOf<String, ConstantValue>()
        val resolving = mutableSetOf<String>()

        fun resolve(name: String): ConstantValue? {
            if (resolved.containsKey(name)) return resolved[name]
            if (resolving.contains(name)) return null // cycle
            val globalVar = validCandidates[name] ?: return null
            val initExpr = globalVar.expression ?: return null

            resolving.add(name)
            val eval = evaluateConstantExpression(initExpr, resolved)
            resolving.remove(name)

            if (eval != null) {
                val coerced = when (globalVar.lslType) {
                    LslPrimitiveType.INTEGER -> when (eval) {
                        is ConstantValue.IntVal -> eval
                        is ConstantValue.FloatVal -> ConstantValue.IntVal(eval.value.toInt())
                        else -> null
                    }
                    LslPrimitiveType.FLOAT -> when (eval) {
                        is ConstantValue.FloatVal -> eval
                        is ConstantValue.IntVal -> ConstantValue.FloatVal(eval.value.toFloat())
                        else -> null
                    }
                    LslPrimitiveType.STRING -> when (eval) {
                        is ConstantValue.StrVal -> eval
                        else -> null
                    }
                    else -> null
                }
                if (coerced != null) {
                    resolved[name] = coerced
                    return coerced
                }
            }
            return null
        }

        var changed = true
        while (changed) {
            changed = false
            for (name in validCandidates.keys) {
                if (!resolved.containsKey(name)) {
                    if (resolve(name) != null) {
                        changed = true
                    }
                }
            }
        }

        return resolved
    }

    fun performConstantFoldingAndPropagation(code: String, project: Project): String {
        if (project.isDisposed || code.isBlank()) return code
        val psiFile = try {
            LslElementFactory.createFile(project, code)
        } catch (e: Exception) {
            return code
        }

        val resolvedConstants = identifyConstantVariables(psiFile)

        val replacements = mutableListOf<Pair<TextRange, String>>()

        fun collectReplacements(element: PsiElement) {
            if (element is LslExpression) {
                val isAssignmentTarget = (element.parent as? LslExpressionAssignment)?.lValue == element
                if (!isAssignmentTarget) {
                    val value = evaluateConstantExpression(element, resolvedConstants)
                    if (value != null) {
                        val isAlreadySimpleLiteral = element is LslConstant
                        val isLValueRef = element is LslLValue && element.variableName?.let {
                            resolvedConstants.containsKey(it) && !isShadowedLocally(element, it) && element.item == null
                        } == true
                        val isCompositeFoldable = !isAlreadySimpleLiteral

                        if (isLValueRef || isCompositeFoldable) {
                            replacements.add(element.textRange to value.toLiteralString())
                            return
                        }
                    }
                }
            }

            for (child in element.children) {
                collectReplacements(child)
            }
        }

        for (child in psiFile.children) {
            collectReplacements(child)
        }

        if (replacements.isEmpty()) return code

        val sorted = replacements.sortedByDescending { it.first.startOffset }
        val sb = StringBuilder(code)
        for ((range, repl) in sorted) {
            if (range.startOffset in 0..sb.length && range.endOffset in 0..sb.length && range.startOffset <= range.endOffset) {
                sb.replace(range.startOffset, range.endOffset, repl)
            }
        }

        return sb.toString()
    }

    fun optimizeConstants(code: String, project: Project): String =
        performConstantFoldingAndPropagation(code, project)

    fun optimizeConstants(project: Project, code: String): String =
        performConstantFoldingAndPropagation(code, project)

    fun performInlining(code: String, project: Project): InliningResult {
        if (project.isDisposed || code.isBlank()) {
            return InliningResult(code, emptySet(), emptySet())
        }

        val initialPsi = try {
            LslElementFactory.createFile(project, code)
        } catch (e: Exception) {
            return InliningResult(code, emptySet(), emptySet())
        }

        val candidateGlobals = mutableMapOf<String, LslGlobalVariable>()
        val candidateFunctions = mutableMapOf<String, LslFunction>()
        val pendingComments = mutableListOf<String>()

        var curr = initialPsi.firstChild
        while (curr != null) {
            when (curr) {
                is com.intellij.psi.PsiWhiteSpace -> {}
                is com.intellij.psi.PsiComment -> {
                    pendingComments.add(curr.text)
                }
                is LslGlobalVariable -> {
                    val isInline = pendingComments.any { isInlineDirectiveComment(it) }
                    pendingComments.clear()
                    if (isInline && curr.name != null) {
                        candidateGlobals[curr.name!!] = curr
                    }
                }
                is LslFunction -> {
                    val isInline = pendingComments.any { isInlineDirectiveComment(it) }
                    pendingComments.clear()
                    if (isInline && curr.name != null) {
                        candidateFunctions[curr.name!!] = curr
                    }
                }
                is LslState -> {
                    pendingComments.clear()
                }
            }
            curr = curr.nextSibling
        }

        // Recursion detection
        val validInlinedFunctions = candidateFunctions.toMutableMap()
        val callGraph = mutableMapOf<String, MutableSet<String>>()
        for ((name, func) in candidateFunctions) {
            val calls = PsiTreeUtil.collectElementsOfType(func.body, LslExpressionFunctionCall::class.java)
            val called = calls.mapNotNull { it.functionName }.filter { it in candidateFunctions.keys }.toMutableSet()
            callGraph[name] = called
        }

        fun hasCycle(current: String, target: String, visited: MutableSet<String>): Boolean {
            val targets = callGraph[current] ?: return false
            for (next in targets) {
                if (next == target) return true
                if (visited.add(next)) {
                    if (hasCycle(next, target, visited)) return true
                }
            }
            return false
        }

        for (funcName in candidateFunctions.keys) {
            if (hasCycle(funcName, funcName, mutableSetOf())) {
                Logger.getInstance(LslPreprocessorEngine::class.java)
                    .warn("Recursive inline function detected: $funcName. Ignoring #inline directive and falling back to standard function inclusion.")
                System.err.println("Warning: Recursive inline function detected: $funcName. Ignoring #inline directive and falling back to standard function inclusion.")
                validInlinedFunctions.remove(funcName)
            }
        }

        var currentCode = code
        val inlinedGlobalNames = candidateGlobals.keys.toSet()

        // 1. Inline Global Variables
        if (inlinedGlobalNames.isNotEmpty()) {
            val psi = try {
                LslElementFactory.createFile(project, currentCode)
            } catch (e: Exception) {
                null
            }
            if (psi != null) {
                val globalsMap = psi.children.filterIsInstance<LslGlobalVariable>()
                    .filter { it.name in inlinedGlobalNames }
                    .associateBy { it.name!! }

                val lValuesToReplace = mutableListOf<Pair<TextRange, String>>()
                val allLValues = PsiTreeUtil.collectElementsOfType(psi, LslLValue::class.java)

                for (lValue in allLValues) {
                    val varName = lValue.variableName ?: continue
                    val globalVar = globalsMap[varName] ?: continue
                    if (PsiTreeUtil.isAncestor(globalVar, lValue, false)) continue
                    if (isShadowedLocally(lValue, varName)) continue

                    val rawExpr = globalVar.expression?.text ?: defaultForType(globalVar.lslType)
                    val replacement = if (isSimpleAtomicExpression(globalVar.expression)) rawExpr else "($rawExpr)"
                    lValuesToReplace.add(lValue.textRange to replacement)
                }

                if (lValuesToReplace.isNotEmpty()) {
                    val sorted = lValuesToReplace.sortedByDescending { it.first.startOffset }
                    val sb = StringBuilder(currentCode)
                    for ((range, repl) in sorted) {
                        if (range.startOffset in 0..sb.length && range.endOffset in 0..sb.length && range.startOffset <= range.endOffset) {
                            sb.replace(range.startOffset, range.endOffset, repl)
                        }
                    }
                    currentCode = sb.toString()
                }
            }
        }

        // 2. Inline Functions
        val inlinedFunctionNames = validInlinedFunctions.keys.toSet()
        val callsiteCounter = AtomicInteger(0)

        if (inlinedFunctionNames.isNotEmpty()) {
            var maxPasses = 50
            while (maxPasses-- > 0) {
                val psi = try {
                    LslElementFactory.createFile(project, currentCode)
                } catch (e: Exception) {
                    break
                }
                val funcsMap = psi.children.filterIsInstance<LslFunction>()
                    .filter { it.name in inlinedFunctionNames }
                    .associateBy { it.name!! }
                if (funcsMap.isEmpty()) break

                val allCalls = PsiTreeUtil.collectElementsOfType(psi, LslExpressionFunctionCall::class.java)
                val targetCalls = allCalls.filter { call ->
                    val name = call.functionName
                    name != null && name in inlinedFunctionNames
                }
                if (targetCalls.isEmpty()) break

                val callToInline = targetCalls.firstOrNull { call ->
                    val innerCalls = PsiTreeUtil.collectElementsOfType(call, LslExpressionFunctionCall::class.java)
                    innerCalls.all { it == call || it.functionName !in inlinedFunctionNames }
                } ?: targetCalls.first()

                val funcName = callToInline.functionName!!
                val func = funcsMap[funcName] ?: break

                if (isSingleExpressionReturn(func)) {
                    currentCode = expandSingleExpressionCall(currentCode, callToInline, func)
                } else {
                    currentCode = expandBlockFunctionCall(currentCode, callToInline, func, callsiteCounter.incrementAndGet())
                }
            }
        }

        return InliningResult(
            transformedCode = currentCode,
            inlinedGlobals = inlinedGlobalNames,
            inlinedFunctions = inlinedFunctionNames
        )
    }

    private fun isSimpleAtomicExpression(expr: LslExpression?): Boolean {
        if (expr == null) return true
        if (expr is LslConstant || expr is LslExpressionParentheses) return true
        val text = expr.text.trim()
        if (text.startsWith("\"") && text.endsWith("\"")) return true
        if (text.startsWith("<") && text.endsWith(">")) return true
        if (text.startsWith("[") && text.endsWith("]")) return true
        if (text.matches(Regex("""^-?\d+(\.\d+)?([eE][+-]?\d+)?$"""))) return true
        return false
    }

    private fun isSingleExpressionReturn(func: LslFunction): Boolean {
        val block = func.body as? LslStatementBlock ?: return false
        val stmts = block.statements
        if (stmts.size != 1) return false
        val ret = stmts[0] as? LslStatementReturn ?: return false
        return ret.expression != null
    }

    private fun isBalanced(s: String): Boolean {
        if (!s.startsWith("(") || !s.endsWith(")")) return false
        var depth = 0
        for (i in s.indices) {
            if (s[i] == '(') depth++
            else if (s[i] == ')') {
                depth--
                if (depth == 0 && i < s.length - 1) return false
            }
        }
        return depth == 0
    }

    private fun formatArgument(argText: String): String {
        val trimmed = argText.trim()
        if (trimmed.startsWith("(") && trimmed.endsWith(")") && isBalanced(trimmed)) {
            return trimmed
        }
        return "($trimmed)"
    }

    private fun expandSingleExpressionCall(
        code: String,
        call: LslExpressionFunctionCall,
        func: LslFunction
    ): String {
        val block = func.body as LslStatementBlock
        val retStmt = block.statements[0] as LslStatementReturn
        val retExpr = retStmt.expression!!

        val paramNames = func.arguments.mapNotNull { it.name }
        val argTexts = call.expressions.map { it.text }

        val paramValues = mutableMapOf<String, String>()
        for ((idx, paramName) in paramNames.withIndex()) {
            val argText = argTexts.getOrNull(idx) ?: defaultForType(func.arguments[idx].lslType)
            paramValues[paramName] = formatArgument(argText)
        }

        val lValues = PsiTreeUtil.collectElementsOfType(retExpr, LslLValue::class.java)
        val replacements = mutableListOf<Pair<TextRange, String>>()
        for (lValue in lValues) {
            val vName = lValue.variableName ?: continue
            val paramVal = paramValues[vName] ?: continue
            val parent = lValue.parent
            if (parent is LslExpressionParentheses && parent.expression == lValue) {
                replacements.add(parent.textRange to paramVal)
            } else {
                replacements.add(lValue.textRange to paramVal)
            }
        }

        val exprStart = retExpr.textRange.startOffset
        val rawExprText = retExpr.text

        val sbExpr = StringBuilder(rawExprText)
        for ((range, repl) in replacements.sortedByDescending { it.first.startOffset }) {
            val startInExpr = range.startOffset - exprStart
            val endInExpr = range.endOffset - exprStart
            if (startInExpr in 0..sbExpr.length && endInExpr in 0..sbExpr.length && startInExpr <= endInExpr) {
                sbExpr.replace(startInExpr, endInExpr, repl)
            }
        }

        val rawExpanded = sbExpr.toString().trim()
        val expandedExpr = if (isBalanced(rawExpanded)) rawExpanded else "($rawExpanded)"
        val callRange = call.textRange

        val sbCode = StringBuilder(code)
        if (callRange.startOffset in 0..sbCode.length && callRange.endOffset in 0..sbCode.length) {
            sbCode.replace(callRange.startOffset, callRange.endOffset, expandedExpr)
        }
        return sbCode.toString()
    }

    private fun expandBlockFunctionCall(
        code: String,
        call: LslExpressionFunctionCall,
        func: LslFunction,
        callsiteId: Int
    ): String {
        val paramMap = mutableMapOf<String, String>()
        val localMap = mutableMapOf<String, String>()
        val labelMap = mutableMapOf<String, String>()

        for (arg in func.arguments) {
            if (arg.name != null) {
                paramMap[arg.name!!] = "__inline_${func.name}_${arg.name}_$callsiteId"
            }
        }

        val localVars = PsiTreeUtil.collectElementsOfType(func.body, LslStatementVariable::class.java)
        for (localVar in localVars) {
            if (localVar.name != null) {
                localMap[localVar.name!!] = "__inline_${func.name}_${localVar.name}_$callsiteId"
            }
        }

        val labels = PsiTreeUtil.collectElementsOfType(func.body, LslStatementLabel::class.java)
        for (lbl in labels) {
            if (lbl.name != null) {
                labelMap[lbl.name!!] = "__inline_${func.name}_${lbl.name}_$callsiteId"
            }
        }
        val jumps = PsiTreeUtil.collectElementsOfType(func.body, LslStatementJump::class.java)
        for (jmp in jumps) {
            if (jmp.labelName != null) {
                labelMap[jmp.labelName!!] = "__inline_${func.name}_${jmp.labelName}_$callsiteId"
            }
        }

        val returnVarName = "__inline_${func.name}_ret_$callsiteId"
        val endLabelName = "__inline_${func.name}_end_$callsiteId"

        val stmts = mutableListOf<String>()
        for ((idx, arg) in func.arguments.withIndex()) {
            val argVal = call.expressions.getOrNull(idx)?.text ?: defaultForType(arg.lslType)
            val renamed = paramMap[arg.name]!!
            stmts.add("${arg.lslType} $renamed = ($argVal);")
        }

        val isVoid = func.lslType == LslPrimitiveType.VOID || func.typeNameEl == null
        if (!isVoid) {
            stmts.add("${func.lslType} $returnVarName;")
        }

        val bodyBlock = func.body as? LslStatementBlock
        val bodyStatements = bodyBlock?.statements.orEmpty()
        var usedEndLabel = false

        for (stmt in bodyStatements) {
            if (stmt is LslStatementReturn) {
                if (stmt.expression != null) {
                    val transformedExpr = transformExpressionText(stmt.expression!!, paramMap, localMap)
                    stmts.add("$returnVarName = $transformedExpr;")
                }
                if (stmt != bodyStatements.lastOrNull()) {
                    stmts.add("jump $endLabelName;")
                    usedEndLabel = true
                }
            } else {
                val transformedStmt = transformStatementWithReturns(
                    stmt = stmt,
                    paramMap = paramMap,
                    localMap = localMap,
                    labelMap = labelMap,
                    returnVarName = returnVarName,
                    endLabelName = endLabelName,
                    onUsedEndLabel = { usedEndLabel = true }
                )
                stmts.add(transformedStmt)
            }
        }

        if (usedEndLabel) {
            stmts.add("@$endLabelName;")
        }

        var enclosingStmt: LslStatement? = PsiTreeUtil.getParentOfType(call, LslStatement::class.java)
        while (enclosingStmt != null && enclosingStmt.parent !is LslStatementBlock) {
            val parent = PsiTreeUtil.getParentOfType(enclosingStmt, LslStatement::class.java) ?: break
            enclosingStmt = parent
        }

        val sbCode = StringBuilder(code)
        if (enclosingStmt != null) {
            val isDirectExprStmt = enclosingStmt is LslStatementExpression &&
                    enclosingStmt.text.trim().removeSuffix(";").trim() == call.text.trim()

            if (isDirectExprStmt) {
                val range = enclosingStmt.textRange
                if (range.startOffset in 0..sbCode.length && range.endOffset in 0..sbCode.length) {
                    sbCode.replace(range.startOffset, range.endOffset, stmts.joinToString("\n"))
                }
            } else {
                val callRange = call.textRange
                if (callRange.startOffset in 0..sbCode.length && callRange.endOffset in 0..sbCode.length) {
                    sbCode.replace(callRange.startOffset, callRange.endOffset, returnVarName)
                    val insertionPos = enclosingStmt.textRange.startOffset
                    sbCode.insert(insertionPos, stmts.joinToString("\n") + "\n")
                }
            }
        } else {
            val callRange = call.textRange
            if (callRange.startOffset in 0..sbCode.length && callRange.endOffset in 0..sbCode.length) {
                sbCode.replace(callRange.startOffset, callRange.endOffset, if (!isVoid) returnVarName else "")
            }
        }

        return sbCode.toString()
    }

    private fun transformExpressionText(
        expr: LslExpression,
        paramMap: Map<String, String>,
        localMap: Map<String, String>
    ): String {
        val lValues = PsiTreeUtil.collectElementsOfType(expr, LslLValue::class.java)
        val replacements = mutableListOf<Pair<TextRange, String>>()
        for (lValue in lValues) {
            val varName = lValue.variableName ?: continue
            val renamed = localMap[varName] ?: paramMap[varName] ?: continue
            val idEl = lValue.variableNameIdentifier ?: lValue
            replacements.add(idEl.textRange to renamed)
        }

        val exprStart = expr.textRange.startOffset
        val sb = StringBuilder(expr.text)
        for ((range, repl) in replacements.sortedByDescending { it.first.startOffset }) {
            val startInExpr = range.startOffset - exprStart
            val endInExpr = range.endOffset - exprStart
            if (startInExpr in 0..sb.length && endInExpr in 0..sb.length && startInExpr <= endInExpr) {
                sb.replace(startInExpr, endInExpr, repl)
            }
        }
        return sb.toString()
    }

    private fun transformStatementWithReturns(
        stmt: LslStatement,
        paramMap: Map<String, String>,
        localMap: Map<String, String>,
        labelMap: Map<String, String>,
        returnVarName: String,
        endLabelName: String,
        onUsedEndLabel: () -> Unit
    ): String {
        val replacements = mutableListOf<Pair<TextRange, String>>()

        val returns = PsiTreeUtil.collectElementsOfType(stmt, LslStatementReturn::class.java)
        for (ret in returns) {
            val retText = if (ret.expression != null) {
                val transformedExpr = transformExpressionText(ret.expression!!, paramMap, localMap)
                "{ $returnVarName = $transformedExpr; jump $endLabelName; }"
            } else {
                "{ jump $endLabelName; }"
            }
            onUsedEndLabel()
            replacements.add(ret.textRange to retText)
        }

        val lValues = PsiTreeUtil.collectElementsOfType(stmt, LslLValue::class.java)
        for (lValue in lValues) {
            if (returns.any { PsiTreeUtil.isAncestor(it, lValue, false) }) continue
            val varName = lValue.variableName ?: continue
            val renamed = localMap[varName] ?: paramMap[varName] ?: continue
            val idEl = lValue.variableNameIdentifier ?: lValue
            replacements.add(idEl.textRange to renamed)
        }

        val variables = PsiTreeUtil.collectElementsOfType(stmt, LslStatementVariable::class.java)
        for (variable in variables) {
            if (returns.any { PsiTreeUtil.isAncestor(it, variable, false) }) continue
            val varName = variable.name ?: continue
            val renamed = localMap[varName] ?: continue
            val idEl = variable.nameIdentifier ?: variable
            replacements.add(idEl.textRange to renamed)
        }

        val labels = PsiTreeUtil.collectElementsOfType(stmt, LslStatementLabel::class.java)
        for (lbl in labels) {
            if (returns.any { PsiTreeUtil.isAncestor(it, lbl, false) }) continue
            val lblName = lbl.name ?: continue
            val renamed = labelMap[lblName] ?: continue
            val idEl = lbl.nameIdentifier ?: lbl
            replacements.add(idEl.textRange to renamed)
        }

        val jumps = PsiTreeUtil.collectElementsOfType(stmt, LslStatementJump::class.java)
        for (jmp in jumps) {
            if (returns.any { PsiTreeUtil.isAncestor(it, jmp, false) }) continue
            val lblName = jmp.labelName ?: continue
            val renamed = labelMap[lblName] ?: continue
            val idEl = jmp.labelNameIdentifier ?: jmp
            replacements.add(idEl.textRange to renamed)
        }

        val stmtStart = stmt.textRange.startOffset
        val sb = StringBuilder(stmt.text)
        for ((range, repl) in replacements.sortedByDescending { it.first.startOffset }) {
            val startInStmt = range.startOffset - stmtStart
            val endInStmt = range.endOffset - stmtStart
            if (startInStmt in 0..sb.length && endInStmt in 0..sb.length && startInStmt <= endInStmt) {
                sb.replace(startInStmt, endInStmt, repl)
            }
        }
        return sb.toString()
    }

    private fun defaultForType(type: LslPrimitiveType?): String {
        return when (type) {
            LslPrimitiveType.INTEGER -> "0"
            LslPrimitiveType.FLOAT -> "0.0"
            LslPrimitiveType.STRING -> "\"\""
            LslPrimitiveType.KEY -> "NULL_KEY"
            LslPrimitiveType.VECTOR -> "<0.0, 0.0, 0.0>"
            LslPrimitiveType.QUATERNION -> "<0.0, 0.0, 0.0, 1.0>"
            LslPrimitiveType.LIST -> "[]"
            else -> "0"
        }
    }

    private fun isShadowedLocally(from: PsiElement, name: String): Boolean {
        var node: PsiElement? = from.parent
        while (node != null && node !is LslFile) {
            when (node) {
                is LslStatementBlock -> {
                    val shadowed = node.children
                        .takeWhile { child -> child != from && (child.textOffset < from.textOffset) }
                        .filterIsInstance<LslStatementVariable>()
                        .any { it.name == name }
                    if (shadowed) return true
                }
                is LslEvent -> {
                    if (node.arguments.any { it.name == name }) return true
                }
                is LslFunction -> {
                    if (node.arguments.any { it.name == name }) return true
                }
            }
            node = node.parent
        }
        return false
    }

    private fun collectIncludedFiles(
        file: PsiFile?,
        project: Project,
        result: MutableSet<PsiFile>,
        visitedFiles: MutableSet<String>,
        depth: Int = 0
    ) {
        if (depth > MAX_INCLUDE_DEPTH || file == null || !file.isValid || project.isDisposed) return
        val text = try {
            file.text
        } catch (e: Exception) {
            null
        } ?: return

        val lines = getLines(text)
        val stack = ArrayDeque<BlockState>()
        val definitions = mutableMapOf<String, String>()

        for (line in lines) {
            val trimmed = line.text.trim()
            val match = DIRECTIVE_REGEX.find(trimmed) ?: continue
            val directive = match.groupValues.getOrNull(1)?.lowercase() ?: continue
            val rawArgs = match.groupValues.getOrNull(2) ?: ""
            val args = stripTrailingComment(rawArgs).trim()

            when (directive) {
                "ifdef" -> {
                    val parentActive = isCurrentlyActive(stack)
                    val ident = args.split(Regex("""\s+""")).firstOrNull()?.trim() ?: ""
                    val cond = parentActive && ident.isNotEmpty() && definitions.containsKey(ident)
                    stack.addLast(BlockState(parentActive = parentActive, conditionMet = cond, currentBranchActive = cond))
                }
                "ifndef" -> {
                    val parentActive = isCurrentlyActive(stack)
                    val ident = args.split(Regex("""\s+""")).firstOrNull()?.trim() ?: ""
                    val cond = parentActive && (ident.isEmpty() || !definitions.containsKey(ident))
                    stack.addLast(BlockState(parentActive = parentActive, conditionMet = cond, currentBranchActive = cond))
                }
                "if" -> {
                    val parentActive = isCurrentlyActive(stack)
                    val cond = parentActive && evaluateCondition(args, definitions)
                    stack.addLast(BlockState(parentActive = parentActive, conditionMet = cond, currentBranchActive = cond))
                }
                "elif" -> {
                    if (stack.isNotEmpty()) {
                        val top = stack.last()
                        val cond = top.parentActive && !top.conditionMet && evaluateCondition(args, definitions)
                        top.currentBranchActive = cond
                        if (cond) top.conditionMet = true
                    }
                }
                "else" -> {
                    if (stack.isNotEmpty()) {
                        val top = stack.last()
                        val cond = top.parentActive && !top.conditionMet
                        top.currentBranchActive = cond
                        top.conditionMet = true
                    }
                }
                "endif" -> {
                    if (stack.isNotEmpty()) {
                        stack.removeLast()
                    }
                }
                "define" -> {
                    if (isCurrentlyActive(stack)) {
                        parseAndAddDefine(args, definitions)
                    }
                }
                "undef" -> {
                    if (isCurrentlyActive(stack)) {
                        val ident = args.split(Regex("""\s+""")).firstOrNull()?.trim() ?: ""
                        if (ident.isNotEmpty()) {
                            definitions.remove(ident)
                        }
                    }
                }
                "include" -> {
                    if (isCurrentlyActive(stack)) {
                        val includedPath = args.trim().trim('"', '<', '>')
                        if (includedPath.isNotEmpty()) {
                            val cleanName = includedPath.substringAfterLast('/').substringAfterLast('\\')
                            if (cleanName.isNotEmpty() && (visitedFiles.add(cleanName) || visitedFiles.add(includedPath))) {
                                val projectRoot = try {
                                    if (project.basePath != null && file.virtualFile?.isInLocalFileSystem == true) {
                                        com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(project.basePath!!)
                                    } else null
                                } catch (e: Exception) {
                                    null
                                }
                                val virtualFile = try {
                                    file.virtualFile?.parent?.findFileByRelativePath(includedPath)
                                        ?: file.virtualFile?.parent?.findFileByRelativePath("$includedPath.lslp")
                                        ?: file.virtualFile?.parent?.findFileByRelativePath("$includedPath.lslm")
                                        ?: file.virtualFile?.parent?.findFileByRelativePath("$includedPath.lsl")
                                        ?: projectRoot?.findFileByRelativePath(includedPath)
                                        ?: projectRoot?.findFileByRelativePath("$includedPath.lslp")
                                        ?: projectRoot?.findFileByRelativePath("$includedPath.lslm")
                                        ?: projectRoot?.findFileByRelativePath("$includedPath.lsl")
                                        ?: FilenameIndex.getFilesByName(project, cleanName, GlobalSearchScope.allScope(project)).firstOrNull()?.virtualFile
                                        ?: FilenameIndex.getFilesByName(project, "$cleanName.lslp", GlobalSearchScope.allScope(project)).firstOrNull()?.virtualFile
                                        ?: FilenameIndex.getFilesByName(project, "$cleanName.lslm", GlobalSearchScope.allScope(project)).firstOrNull()?.virtualFile
                                        ?: FilenameIndex.getFilesByName(project, "$cleanName.lsl", GlobalSearchScope.allScope(project)).firstOrNull()?.virtualFile
                                } catch (e: Exception) {
                                    null
                                }
                                if (virtualFile != null) {
                                    val canonical = virtualFile.canonicalPath ?: virtualFile.path
                                    if (visitedFiles.add(canonical) || visitedFiles.add(virtualFile.path)) {
                                        val includedPsiFile = try {
                                            PsiManager.getInstance(project).findFile(virtualFile)
                                        } catch (e: Exception) {
                                            null
                                        }
                                        if (includedPsiFile != null && includedPsiFile.isValid) {
                                            result.add(includedPsiFile)
                                            collectIncludedFiles(includedPsiFile, project, result, visitedFiles, depth + 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isCurrentlyActive(stack: ArrayDeque<BlockState>): Boolean {
        return stack.isEmpty() || stack.all { it.currentBranchActive }
    }

    private fun processInclude(
        includedPath: String,
        project: Project,
        containingFile: PsiFile?,
        definitions: MutableMap<String, String>,
        visitedFiles: MutableSet<String>,
        depth: Int = 0
    ) {
        if (depth > MAX_INCLUDE_DEPTH || project.isDisposed || includedPath.isEmpty()) return
        val cleanName = includedPath.substringAfterLast('/').substringAfterLast('\\')
        if (cleanName.isEmpty()) return

        if (!visitedFiles.add(cleanName) && !visitedFiles.add(includedPath)) {
            return
        }

        val projectRoot = try {
            if (project.basePath != null && containingFile?.virtualFile?.isInLocalFileSystem == true) {
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(project.basePath!!)
            } else null
        } catch (e: Exception) {
            null
        }
        val virtualFile = try {
            containingFile?.virtualFile?.parent?.findFileByRelativePath(includedPath)
                ?: containingFile?.virtualFile?.parent?.findFileByRelativePath("$includedPath.lslp")
                ?: containingFile?.virtualFile?.parent?.findFileByRelativePath("$includedPath.lslm")
                ?: containingFile?.virtualFile?.parent?.findFileByRelativePath("$includedPath.lsl")
                ?: projectRoot?.findFileByRelativePath(includedPath)
                ?: projectRoot?.findFileByRelativePath("$includedPath.lslp")
                ?: projectRoot?.findFileByRelativePath("$includedPath.lslm")
                ?: projectRoot?.findFileByRelativePath("$includedPath.lsl")
                ?: FilenameIndex.getFilesByName(project, cleanName, GlobalSearchScope.allScope(project)).firstOrNull()?.virtualFile
                ?: FilenameIndex.getFilesByName(project, "$cleanName.lslp", GlobalSearchScope.allScope(project)).firstOrNull()?.virtualFile
                ?: FilenameIndex.getFilesByName(project, "$cleanName.lslm", GlobalSearchScope.allScope(project)).firstOrNull()?.virtualFile
                ?: FilenameIndex.getFilesByName(project, "$cleanName.lsl", GlobalSearchScope.allScope(project)).firstOrNull()?.virtualFile
        } catch (e: Exception) {
            null
        } ?: return

        val canonicalPath = virtualFile.canonicalPath ?: virtualFile.path
        if (!visitedFiles.add(canonicalPath) || !visitedFiles.add(virtualFile.path)) return

        val psiFile = try {
            PsiManager.getInstance(project).findFile(virtualFile)
        } catch (e: Exception) {
            null
        } ?: return

        val text = try {
            psiFile.text
        } catch (e: Exception) {
            null
        } ?: return

        val lines = getLines(text)
        val stack = ArrayDeque<BlockState>()

        for (line in lines) {
            val trimmed = line.text.trim()
            val match = DIRECTIVE_REGEX.find(trimmed) ?: continue
            val directive = match.groupValues.getOrNull(1)?.lowercase() ?: continue
            val rawArgs = match.groupValues.getOrNull(2) ?: ""
            val args = stripTrailingComment(rawArgs).trim()

            when (directive) {
                "ifdef" -> {
                    val parentActive = isCurrentlyActive(stack)
                    val ident = args.split(Regex("""\s+""")).firstOrNull()?.trim() ?: ""
                    val cond = parentActive && ident.isNotEmpty() && definitions.containsKey(ident)
                    stack.addLast(BlockState(parentActive = parentActive, conditionMet = cond, currentBranchActive = cond))
                }
                "ifndef" -> {
                    val parentActive = isCurrentlyActive(stack)
                    val ident = args.split(Regex("""\s+""")).firstOrNull()?.trim() ?: ""
                    val cond = parentActive && (ident.isEmpty() || !definitions.containsKey(ident))
                    stack.addLast(BlockState(parentActive = parentActive, conditionMet = cond, currentBranchActive = cond))
                }
                "if" -> {
                    val parentActive = isCurrentlyActive(stack)
                    val cond = parentActive && evaluateCondition(args, definitions)
                    stack.addLast(BlockState(parentActive = parentActive, conditionMet = cond, currentBranchActive = cond))
                }
                "elif" -> {
                    if (stack.isNotEmpty()) {
                        val top = stack.last()
                        val cond = top.parentActive && !top.conditionMet && evaluateCondition(args, definitions)
                        top.currentBranchActive = cond
                        if (cond) top.conditionMet = true
                    }
                }
                "else" -> {
                    if (stack.isNotEmpty()) {
                        val top = stack.last()
                        val cond = top.parentActive && !top.conditionMet
                        top.currentBranchActive = cond
                        top.conditionMet = true
                    }
                }
                "endif" -> {
                    if (stack.isNotEmpty()) {
                        stack.removeLast()
                    }
                }
                "define" -> {
                    if (isCurrentlyActive(stack)) {
                        parseAndAddDefine(args, definitions)
                    }
                }
                "undef" -> {
                    if (isCurrentlyActive(stack)) {
                        val ident = args.split(Regex("""\s+""")).firstOrNull()?.trim() ?: ""
                        if (ident.isNotEmpty()) {
                            definitions.remove(ident)
                        }
                    }
                }
                "include" -> {
                    if (isCurrentlyActive(stack)) {
                        val nestedPath = args.trim().trim('"', '<', '>')
                        processInclude(nestedPath, project, psiFile, definitions, visitedFiles, depth + 1)
                    }
                }
            }
        }
    }

    private fun stripTrailingComment(s: String): String {
        var inString = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '"') {
                inString = !inString
            } else if (!inString && c == '/' && i + 1 < s.length && s[i + 1] == '/') {
                return s.substring(0, i).trim()
            } else if (!inString && c == '/' && i + 1 < s.length && s[i + 1] == '*') {
                val closeIdx = s.indexOf("*/", i + 2)
                return if (closeIdx != -1) {
                    (s.substring(0, i) + s.substring(closeIdx + 2)).trim()
                } else {
                    s.substring(0, i).trim()
                }
            }
            i++
        }
        return s.trim()
    }

    private fun getLines(text: String): List<LineInfo> {
        val lines = mutableListOf<LineInfo>()
        var lineStart = 0
        val length = text.length
        var i = 0
        while (i < length) {
            if (text[i] == '\r') {
                val end = i
                if (i + 1 < length && text[i + 1] == '\n') {
                    i += 2
                } else {
                    i++
                }
                lines.add(LineInfo(lineStart, end, text.substring(lineStart, end)))
                lineStart = i
            } else if (text[i] == '\n') {
                val end = i
                i++
                lines.add(LineInfo(lineStart, end, text.substring(lineStart, end)))
                lineStart = i
            } else {
                i++
            }
        }
        if (lineStart <= length) {
            lines.add(LineInfo(lineStart, length, text.substring(lineStart, length)))
        }
        return lines
    }

    private fun mergeContiguousRanges(ranges: List<TextRange>): List<TextRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.startOffset }
        val merged = mutableListOf<TextRange>()
        var currentStart = sorted[0].startOffset
        var currentEnd = sorted[0].endOffset

        for (i in 1 until sorted.size) {
            val r = sorted[i]
            if (r.startOffset <= currentEnd + 2) {
                currentEnd = maxOf(currentEnd, r.endOffset)
            } else {
                merged.add(TextRange(currentStart, currentEnd))
                currentStart = r.startOffset
                currentEnd = r.endOffset
            }
        }
        merged.add(TextRange(currentStart, currentEnd))
        return merged
    }

    data class LineInfo(val startOffset: Int, val endOffset: Int, val text: String)

    data class BlockState(
        val parentActive: Boolean,
        var conditionMet: Boolean,
        var currentBranchActive: Boolean
    )

    enum class TokenType {
        LPAREN, RPAREN, OR, AND, EQ, NOT_EQ, NOT, IDENTIFIER, STRING, NUMBER
    }

    data class Token(val type: TokenType, val text: String)

    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val n = input.length
        while (i < n) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { tokens.add(Token(TokenType.LPAREN, "(")); i++ }
                c == ')' -> { tokens.add(Token(TokenType.RPAREN, ")")); i++ }
                c == '|' -> {
                    if (i + 1 < n && input[i + 1] == '|') {
                        tokens.add(Token(TokenType.OR, "||"))
                        i += 2
                    } else {
                        tokens.add(Token(TokenType.OR, "|"))
                        i++
                    }
                }
                c == '&' -> {
                    if (i + 1 < n && input[i + 1] == '&') {
                        tokens.add(Token(TokenType.AND, "&&"))
                        i += 2
                    } else {
                        tokens.add(Token(TokenType.AND, "&"))
                        i++
                    }
                }
                c == '=' -> {
                    if (i + 1 < n && input[i + 1] == '=') {
                        tokens.add(Token(TokenType.EQ, "=="))
                        i += 2
                    } else {
                        tokens.add(Token(TokenType.EQ, "="))
                        i++
                    }
                }
                c == '!' -> {
                    if (i + 1 < n && input[i + 1] == '=') {
                        tokens.add(Token(TokenType.NOT_EQ, "!="))
                        i += 2
                    } else {
                        tokens.add(Token(TokenType.NOT, "!"))
                        i++
                    }
                }
                c == '"' -> {
                    val sb = StringBuilder()
                    i++
                    while (i < n && input[i] != '"') {
                        if (input[i] == '\\' && i + 1 < n) {
                            i++
                        }
                        sb.append(input[i])
                        i++
                    }
                    if (i < n && input[i] == '"') i++
                    tokens.add(Token(TokenType.STRING, sb.toString()))
                }
                c.isDigit() -> {
                    val sb = StringBuilder()
                    while (i < n && (input[i].isDigit() || input[i] == 'x' || input[i] == 'X' || (input[i] in 'a'..'f') || (input[i] in 'A'..'F'))) {
                        sb.append(input[i])
                        i++
                    }
                    tokens.add(Token(TokenType.NUMBER, sb.toString()))
                }
                c.isJavaIdentifierStart() || c == '_' -> {
                    val sb = StringBuilder()
                    while (i < n && (input[i].isJavaIdentifierPart() || input[i] == '_')) {
                        sb.append(input[i])
                        i++
                    }
                    tokens.add(Token(TokenType.IDENTIFIER, sb.toString()))
                }
                else -> {
                    i++
                }
            }
        }
        return tokens
    }

    private sealed class ExprValue {
        data class BoolVal(val value: Boolean) : ExprValue()
        data class NumVal(val value: Long) : ExprValue()
        data class StrVal(val value: String) : ExprValue()
        data class IdentVal(val name: String) : ExprValue()
        object Undefined : ExprValue()

        fun toBoolean(): Boolean = when (this) {
            is BoolVal -> value
            is NumVal -> value != 0L
            is StrVal -> value.isNotEmpty() && value != "0" && !value.equals("false", ignoreCase = true)
            is IdentVal -> false
            Undefined -> false
        }

        fun toNormalizedString(): String = when (this) {
            is BoolVal -> value.toString()
            is NumVal -> value.toString()
            is StrVal -> value
            is IdentVal -> name
            Undefined -> ""
        }
    }

    private class ExpressionParser(
        private val tokens: List<Token>,
        private val definitions: Map<String, String>
    ) {
        private var pos = 0

        private fun peek(): Token? = if (pos < tokens.size) tokens[pos] else null
        private fun previous(): Token = tokens[pos - 1]

        private fun check(type: TokenType): Boolean = peek()?.type == type

        private fun match(vararg types: TokenType): Boolean {
            for (type in types) {
                if (check(type)) {
                    pos++
                    return true
                }
            }
            return false
        }

        private fun consume(type: TokenType): Token {
            if (check(type)) return tokens[pos++]
            throw IllegalArgumentException("Expected $type but got ${peek()?.type}")
        }

        fun parse(): Boolean {
            if (tokens.isEmpty()) return false
            return parseOr().toBoolean()
        }

        private fun parseOr(): ExprValue {
            var left = parseAnd()
            while (match(TokenType.OR)) {
                val right = parseAnd()
                left = ExprValue.BoolVal(left.toBoolean() || right.toBoolean())
            }
            return left
        }

        private fun parseAnd(): ExprValue {
            var left = parseEquality()
            while (match(TokenType.AND)) {
                val right = parseEquality()
                left = ExprValue.BoolVal(left.toBoolean() && right.toBoolean())
            }
            return left
        }

        private fun parseEquality(): ExprValue {
            var left = parseUnary()
            while (match(TokenType.EQ, TokenType.NOT_EQ)) {
                val op = previous().type
                val right = parseUnary()
                val eq = areEqual(left, right)
                left = ExprValue.BoolVal(if (op == TokenType.EQ) eq else !eq)
            }
            return left
        }

        private fun parseUnary(): ExprValue {
            if (match(TokenType.NOT)) {
                val right = parseUnary()
                return ExprValue.BoolVal(!right.toBoolean())
            }
            return parsePrimary()
        }

        private fun parsePrimary(): ExprValue {
            if (match(TokenType.LPAREN)) {
                val expr = parseOr()
                consume(TokenType.RPAREN)
                return expr
            }

            if (match(TokenType.NUMBER)) {
                val text = previous().text
                val num = if (text.startsWith("0x", ignoreCase = true)) {
                    text.substring(2).toLongOrNull(16) ?: 0L
                } else {
                    text.toLongOrNull() ?: 0L
                }
                return ExprValue.NumVal(num)
            }

            if (match(TokenType.STRING)) {
                return ExprValue.StrVal(previous().text)
            }

            if (match(TokenType.IDENTIFIER)) {
                val ident = previous().text
                if (ident == "defined") {
                    val hasParen = match(TokenType.LPAREN)
                    if (check(TokenType.IDENTIFIER)) {
                        val target = consume(TokenType.IDENTIFIER).text
                        if (hasParen) consume(TokenType.RPAREN)
                        return ExprValue.BoolVal(definitions.containsKey(target))
                    }
                    return ExprValue.BoolVal(false)
                }
                if (ident.equals("true", ignoreCase = true)) return ExprValue.BoolVal(true)
                if (ident.equals("false", ignoreCase = true)) return ExprValue.BoolVal(false)

                if (definitions.containsKey(ident)) {
                    val rawVal = definitions[ident]?.trim() ?: ""
                    val unquoted = rawVal.removeSurrounding("\"")
                    val num = unquoted.toLongOrNull()
                    return when {
                        num != null -> ExprValue.NumVal(num)
                        unquoted.equals("true", ignoreCase = true) -> ExprValue.BoolVal(true)
                        unquoted.equals("false", ignoreCase = true) -> ExprValue.BoolVal(false)
                        else -> ExprValue.StrVal(unquoted)
                    }
                } else {
                    return ExprValue.IdentVal(ident)
                }
            }

            return ExprValue.Undefined
        }

        private fun areEqual(a: ExprValue, b: ExprValue): Boolean {
            if (a is ExprValue.NumVal && b is ExprValue.NumVal) {
                return a.value == b.value
            }
            val s1 = a.toNormalizedString()
            val s2 = b.toNormalizedString()
            return s1 == s2
        }
    }
}
