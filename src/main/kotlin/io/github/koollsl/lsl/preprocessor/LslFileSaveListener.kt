package io.github.koollsl.lsl.preprocessor

import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import io.github.koollsl.lsl.safeguards.LslBuildOutputNotificationProvider

@Service(Service.Level.PROJECT)
class LslFileSaveListener(private val project: Project) : FileDocumentManagerListener {

    override fun beforeDocumentSaving(document: Document) {
        val file: VirtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        val ext = file.extension?.lowercase() ?: return
        if (ext != "lslp" && ext != "lslm") return
        if (LslBuildOutputNotificationProvider.isGeneratedBuildFile(file)) return
        // Only process files belonging to THIS project
        val belongsToProject = ProjectRootManager.getInstance(project)
            .fileIndex
            .isInContent(file)

        if (!belongsToProject) {
            return
        }
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                WriteCommandAction.runWriteCommandAction(project, "LSL Preprocess File", null, Runnable {
                    PsiDocumentManager.getInstance(project).commitDocument(document)

                    val engine = project.service<LslPreprocessorEngine>()
                    engine.processFileOnSave(file)
                })
            }
        }
    }
}

