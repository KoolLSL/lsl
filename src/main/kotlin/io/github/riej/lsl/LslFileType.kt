package io.github.riej.lsl

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class LslFileType : LanguageFileType(LslLanguage.INSTANCE) {
    override fun getName(): String = "LSL file"
    override fun getDescription(): String = "Linden Script language file"
    override fun getDefaultExtension(): String = "lslp"
    override fun getIcon(): Icon = LslIcons.FILE

    companion object {
        val INSTANCE = LslFileType()
    }
}