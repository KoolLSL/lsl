package io.github.koollsl.lsl.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import io.github.koollsl.lsl.parser.LslTypes

class LslStatementWhile(node: ASTNode) : ASTWrapperPsiElement(node), LslStatement {
    val condition: LslExpression?
        get() = findChildByType(LslTypes.EXPRESSIONS)

    val statement: LslStatement?
        get() = findChildByType(LslTypes.STATEMENTS)
}