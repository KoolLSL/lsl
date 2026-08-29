package io.github.koollsl.lsl.preprocessor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiDocumentManager
import io.github.koollsl.lsl.safeguards.LslBuildOutputNotificationProvider

class LslFileSaveListener : FileDocumentManagerListener {
    override fun beforeDocumentSaving(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val ext = file.extension?.lowercase() ?: return
        if (ext != "lslp" && ext != "lslm") return
        if (LslBuildOutputNotificationProvider.isGeneratedBuildFile(file)) return

        val projects = ProjectManager.getInstance().openProjects

        // Defer PSI commit and preprocessing to EDT after save lock is released
        ApplicationManager.getApplication().invokeLater {
            for (project in projects) {
                if (!project.isDisposed) {
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    LslPreprocessorEngine.processFileOnSave(file, project)
                }
            }
        }
    }
}