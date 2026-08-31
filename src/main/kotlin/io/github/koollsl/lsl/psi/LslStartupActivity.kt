package io.github.koollsl.lsl.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import io.github.koollsl.lsl.preprocessor.LslFileSaveListener

class LslStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        PsiManager.getInstance(project).addPsiTreeChangeListener(
            LslPsiTreeChangeListener(project),
            project // Automatically unregisters when project closes
        )

        // If you also need the save listener registered here:
        project.messageBus.connect().subscribe(
            FileDocumentManagerListener.TOPIC,
            LslFileSaveListener(project)
        )
    }
}
