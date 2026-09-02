package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import io.github.koollsl.lsl.KwdbData
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.LslPrimitiveType
import io.github.koollsl.lsl.parser.LslTypes
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslArgument
import io.github.koollsl.lsl.psi.LslElementFactory
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslEvent
import kotlin.math.min

class LslInvalidEventDeclarationInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Invalid event declaration"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = "Invalid event declaration"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        val kwdbData = KwdbData.getInstance(file.project)
        val preprocessorEngine = file.project.getService(LslPreprocessorEngine::class.java)

        return object : LslElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is LslEvent) return
                if (preprocessorEngine.isDisabledText(file, element.textRange)) return

                val definition = kwdbData.events[element.name]
                if (definition == null) {
                    holder.registerProblem(
                        element,
                        "Unknown event",
                        ProblemHighlightType.ERROR,
                        TextRange(0, element.textLength),
                        RemoveEventFix(element)
                    )
                    return
                }

                val arguments = element.arguments

                if (arguments.isNotEmpty()) {
                    (0 until min(arguments.size, definition.arguments.size)).forEach { i ->
                        val definitionType = definition.arguments[i].lslType
                        val argumentType = arguments[i].lslType

                        if (definitionType.operationTo(argumentType, LslTypes.ASSIGN) == LslPrimitiveType.INVALID) {
                            holder.registerProblem(
                                element,
                                "Type mismatch (expected %s, got %s)".format(definitionType, argumentType),
                                ProblemHighlightType.GENERIC_ERROR,
                                arguments[i].textRangeInParent,
                                ChangeTypeFix(arguments[i], definitionType)
                            )
                        }
                    }
                }

                if (arguments.size < definition.arguments.size) {
                    val missingDefs = definition.arguments.subList(arguments.size, definition.arguments.size)
                    val targetRange = element.parenthesesRightEl?.textRangeInParent
                        ?: TextRange(element.textLength - 1, element.textLength)

                    holder.registerProblem(
                        element,
                        "Wrong arguments count (expected ${definition.arguments.size}, got ${arguments.size})",
                        ProblemHighlightType.GENERIC_ERROR,
                        targetRange,
                        AddMissingArgumentsFix(
                            element,
                            missingDefs.map { "${it.lslType.name.lowercase()} ${it.name}" }
                        )
                    )
                } else if (arguments.size > definition.arguments.size) {
                    val firstExtraArgument = if (definition.arguments.isNotEmpty())
                        arguments[definition.arguments.size]
                    else
                        arguments.first()

                    val firstExtraArgumentComma = element.argumentsEl?.node?.getChildren(null)
                        ?.filter { it.elementType == LslTypes.COMMA }
                        ?.lastOrNull { it.psi.endOffset < firstExtraArgument.startOffset }
                        ?.psi

                    val lastExtraArgument = arguments.last()

                    val startOffset = (firstExtraArgumentComma?.startOffset ?: firstExtraArgument.startOffset) - element.startOffset
                    val endOffset = lastExtraArgument.endOffset - element.startOffset

                    holder.registerProblem(
                        element,
                        "Wrong arguments count (expected ${definition.arguments.size}, got ${arguments.size})",
                        ProblemHighlightType.GENERIC_ERROR,
                        TextRange(startOffset.coerceAtLeast(0), endOffset.coerceAtMost(element.textLength)),
                        RemoveExtraArgumentsFix(
                            firstExtraArgumentComma ?: firstExtraArgument,
                            lastExtraArgument
                        )
                    )
                }
            }
        }
    }

    class RemoveEventFix(event: LslEvent) : LocalQuickFixOnPsiElement(event) {
        override fun getFamilyName(): String = "Remove event"
        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.delete()
        }
    }

    class ChangeTypeFix(argument: LslArgument, val type: LslPrimitiveType) : LocalQuickFixOnPsiElement(argument) {
        override fun getFamilyName(): String = "Change type to $type"
        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val argument = startElement as? LslArgument ?: return
            argument.typeNameEl?.replace(LslElementFactory.createTypeName(project, type))
        }
    }

    class RemoveExtraArgumentsFix(startElement: PsiElement, endElement: PsiElement) :
        LocalQuickFixOnPsiElement(startElement, endElement) {
        override fun getFamilyName(): String = "Remove extra arguments"
        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.parent?.deleteChildRange(startElement, endElement)
        }
    }

    class AddMissingArgumentsFix(event: LslEvent, private val missingArgStrings: List<String>) :
        LocalQuickFixOnPsiElement(event) {
        override fun getFamilyName(): String = "Add missing arguments"
        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val event = startElement as? LslEvent ?: return
            val argumentsEl = event.argumentsEl ?: return

            val existingArgsCount = event.arguments.size
            val prefix = if (existingArgsCount > 0) ", " else ""
            val formattedArgs = prefix + missingArgStrings.joinToString(", ")

            val dummyFile = LslElementFactory.createFile(project, "default { dummy($formattedArgs) {} }")
            val dummyEvent = PsiTreeUtil.findChildOfType(dummyFile, LslEvent::class.java) ?: return
            val dummyArgsEl = dummyEvent.argumentsEl ?: return

            val childrenArray = dummyArgsEl.children
            for (i in childrenArray.indices) {
                argumentsEl.add(childrenArray[i])
            }
        }
    }
}