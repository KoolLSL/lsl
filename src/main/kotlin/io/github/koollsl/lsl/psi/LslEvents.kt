package io.github.koollsl.lsl.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import io.github.koollsl.lsl.parser.LslTypes

class LslEvents(node: ASTNode) : ASTWrapperPsiElement(node) {
    val events: List<LslEvent>
        get() = findChildrenByType(LslTypes.EVENT)
}