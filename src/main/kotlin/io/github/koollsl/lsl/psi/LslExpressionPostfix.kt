package io.github.koollsl.lsl.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import io.github.koollsl.lsl.LslPrimitiveType
import io.github.koollsl.lsl.parser.LslTypes

class LslExpressionPostfix(node: ASTNode) : ASTWrapperPsiElement(node), LslExpression {
    val expression: LslExpression?
        get() = findChildByType(LslTypes.EXPRESSIONS)

    override val lslType: LslPrimitiveType
        get() = expression?.lslType ?: LslPrimitiveType.INVALID
}