package io.github.koollsl.lsl.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode

class LslStatementExpression(node: ASTNode) : ASTWrapperPsiElement(node), LslStatement {
}