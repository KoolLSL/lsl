package io.github.riej.lsl.psi

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent

class LslPsiTreeChangeListener(private val project: Project) : PsiTreeChangeAdapter() {

    override fun childrenChanged(event: PsiTreeChangeEvent) {
        handleChange(event)
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
        handleChange(event)
    }

    override fun childAdded(event: PsiTreeChangeEvent) {
        handleChange(event)
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
        handleChange(event)
    }

    private fun handleChange(event: PsiTreeChangeEvent) {
        val psiFile = event.file as? LslFile ?: return
        val virtualFile = psiFile.virtualFile ?: return

        // Update Project Tree View
        ProjectView.getInstance(project).refresh()

        // Update Tab Presentation
        FileEditorManager.getInstance(project).updateFilePresentation(virtualFile)
    }
}