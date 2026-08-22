package io.github.riej.lsl.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import io.github.riej.lsl.psi.*
import javax.swing.Icon

class LslStructureViewElement(val element: NavigatablePsiElement) : StructureViewTreeElement, SortableTreeElement {
    override fun getPresentation(): ItemPresentation = (element as? ItemPresentation) ?: element.presentation ?: object : ItemPresentation {
        override fun getPresentableText(): String = element.name?.takeUnless { it.isBlank() } ?: "(anonymous)"
        override fun getLocationString(): String? = null
        override fun getIcon(unused: Boolean): Icon? = element.getIcon(0)
    }

    override fun getChildren(): Array<TreeElement> {
        if (element is LslFile) {
            return element.children.mapNotNull {
                when (it) {
                    is LslGlobalVariable -> LslStructureViewElement(it)
                    is LslFunction -> LslStructureViewElement(it)
                    is LslState -> LslStructureViewElement(it)
                    else -> null
                }
            }.toTypedArray()
        }

        if (element is LslState) {
            return element.events.map { LslStructureViewElement(it) }.toTypedArray()
        }

        return emptyArray()
    }

    override fun navigate(requestFocus: Boolean) = element.navigate(requestFocus)

    override fun canNavigate(): Boolean = element.canNavigate()

    override fun canNavigateToSource(): Boolean = element.canNavigateToSource()

    override fun getValue(): Any = element

    override fun getAlphaSortKey(): String = element.name ?: ""
}