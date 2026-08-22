package io.github.riej.lsl.preprocessor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiDocumentManager
import io.github.riej.lsl.safeguards.LslBuildOutputNotificationProvider

class LslFileSaveListener : FileDocumentManagerListener {
    override fun beforeDocumentSaving(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val ext = file.extension?.lowercase() ?: return
        if (ext != "lslp" && ext != "lslm") return
        if (LslBuildOutputNotificationProvider.isGeneratedBuildFile(file)) return

        val projects = ProjectManager.getInstance().openProjects
        for (project in projects) {
            if (!project.isDisposed) {
                PsiDocumentManager.getInstance(project).commitDocument(document)
                LslPreprocessorEngine.processFileOnSave(file, project)
            }
        }
    }
}
