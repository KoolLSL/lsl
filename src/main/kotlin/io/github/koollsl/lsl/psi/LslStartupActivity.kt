package io.github.koollsl.lsl.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi.PsiManager

class LslStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        PsiManager.getInstance(project).addPsiTreeChangeListener(
            LslPsiTreeChangeListener(project),
            project // Automatically unregisters when project closes
        )
    }
}