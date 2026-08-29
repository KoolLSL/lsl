package io.github.koollsl.lsl.syntax

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import io.github.koollsl.lsl.parser.LslTypes

class LslQuoteHandler : SimpleTokenSetQuoteHandler(LslTypes.STRING_CONSTANT) {
}