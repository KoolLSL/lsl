package io.github.koollsl.lsl

import com.intellij.ide.IconProvider
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

object LslIcons {
    val FILE: Icon = IconLoader.getIcon("/icons/lsl.svg", LslIcons::class.java)

    // PSI Structure Icons (used by LslEvent, LslFunction, etc.)
    val EVENT: Icon = IconLoader.getIcon("/icons/event.svg", LslIcons::class.java)
    val STATE: Icon = IconLoader.getIcon("/icons/lsl.svg", LslIcons::class.java) // Or /icons/state.svg if present

    // File Extension Icons
    val FILE_LSL: Icon = IconLoader.getIcon("/icons/lsl.svg", LslIcons::class.java)
    val FILE_LSLP: Icon = IconLoader.getIcon("/icons/lslp.svg", LslIcons::class.java)
    val FILE_LSLM: Icon = IconLoader.getIcon("/icons/lslm.svg", LslIcons::class.java)
}

class LslIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element is PsiFile) {
            return when (element.virtualFile?.extension?.lowercase()) {
                "lsl" -> LslIcons.FILE_LSL
                "lslp" -> LslIcons.FILE_LSLP
                "lslm" -> LslIcons.FILE_LSLM
                else -> null
            }
        }
        return null
    }
}