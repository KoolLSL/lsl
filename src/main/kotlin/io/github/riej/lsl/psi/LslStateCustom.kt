package io.github.riej.lsl.psi

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import io.github.riej.lsl.LslIcons
import javax.swing.Icon

class LslStateCustom(node: ASTNode) : ASTWrapperLslNamedElement(node), LslState, ItemPresentation {
    override fun getPresentableText(): String {
        val identifierName = name.takeUnless { it.isNullOrBlank() } ?: identifyingElement?.text?.takeUnless { it.isBlank() } ?: "(anonymous)"
        return "state $identifierName"
    }

    override fun getIcon(unused: Boolean): Icon = LslIcons.STATE

    override fun getNavigationElement(): PsiElement =
        this.identifyingElement ?: this
}