package io.github.riej.lsl.psi

import com.intellij.navigation.ItemPresentation

interface LslVariable : LslNamedElement, LslTypedElement, ItemPresentation, LslSymbolDeclaration {
    override fun getPresentableText(): String {
        val identifierName = name.takeUnless { it.isNullOrBlank() } ?: identifyingElement?.text?.takeUnless { it.isBlank() } ?: "(anonymous)"
        val text = "$lslType $identifierName".trim()
        return text.ifBlank { "(anonymous)" }
    }

    val expression: LslExpression?
}