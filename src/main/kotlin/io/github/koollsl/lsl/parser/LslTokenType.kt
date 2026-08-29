package io.github.koollsl.lsl.parser

import com.intellij.psi.tree.IElementType
import io.github.koollsl.lsl.LslLanguage
import org.jetbrains.annotations.NonNls

class LslTokenType(debugName: @NonNls String) : IElementType(debugName, LslLanguage.INSTANCE) {
}