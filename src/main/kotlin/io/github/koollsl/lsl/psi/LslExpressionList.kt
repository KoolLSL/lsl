package io.github.koollsl.lsl.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import io.github.koollsl.lsl.LslPrimitiveType
import io.github.koollsl.lsl.parser.LslTypes

class LslExpressionList(node: ASTNode) : ASTWrapperPsiElement(node), LslExpression {
    val expressions: List<LslExpression>
        get() = findChildrenByType(LslTypes.EXPRESSIONS)

    override val lslType: LslPrimitiveType
        get() = LslPrimitiveType.LIST
}