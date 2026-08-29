package io.github.koollsl.lsl.psi

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.packageDependencies.ui.PackageDependenciesNode
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes

class LslProjectViewNodeDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val psiFile = (node as? PsiFileNode)?.value as? LslFile ?: return

        if (PsiTreeUtil.hasErrorElements(psiFile)) {
            val fileName = node.value.name ?: psiFile.name ?: return

            // 1. Capture the existing text attributes (preserves current font color/VCS status)
            val baseAttrs = data.coloredText.firstOrNull()?.attributes
                ?: SimpleTextAttributes.REGULAR_ATTRIBUTES

            // 2. Build new attributes: keep existing foreground/background, add red wavy underline
            val errorWavedAttrs = SimpleTextAttributes(
                baseAttrs.bgColor,
                baseAttrs.fgColor, // Retains original text color
                JBColor.RED,       // Wave line color
                baseAttrs.style or SimpleTextAttributes.STYLE_WAVED
            )

            // 3. Re-apply text with wave attribute
            data.clearText()
            data.addText(fileName, errorWavedAttrs)
        }
    }

    override fun decorate(node: PackageDependenciesNode, cellRenderer: ColoredTreeCellRenderer) {}
}