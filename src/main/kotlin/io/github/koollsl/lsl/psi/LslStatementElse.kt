package io.github.koollsl.lsl.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import io.github.koollsl.lsl.parser.LslTypes

// not a real statement
class LslStatementElse(node: ASTNode) : ASTWrapperPsiElement(node) {
    val statement: LslStatement?
        get() = findChildByType(LslTypes.STATEMENTS)
}