package io.github.riej.lsl.safeguards

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications

class LslBuildOutputNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
    companion object {
        private val KEY = Key.create<EditorNotificationPanel>("io.github.riej.lsl.safeguards.build.output")
        const val WARNING_TEXT = "This is a generated build file. Direct changes will be overwritten during preprocessing."

        fun isGeneratedBuildFile(file: VirtualFile?): Boolean {
            if (file == null) return false
            val normalizedPath = file.path.replace('\\', '/')
            val dirPath = normalizedPath.substringBeforeLast('/', "")
            val segments = dirPath.split('/')
            return segments.any { segment ->
                segment.equals("build", ignoreCase = true) ||
                segment.equals("out", ignoreCase = true) ||
                segment.equals("output", ignoreCase = true) ||
                segment.equals("dist", ignoreCase = true)
            }
        }

        @JvmStatic
        fun isGeneratedBuildLslFile(file: VirtualFile?): Boolean = isGeneratedBuildFile(file)
    }

    override fun getKey(): Key<EditorNotificationPanel> = KEY

    override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
        if (!isGeneratedBuildFile(file)) return null
        val panel = EditorNotificationPanel(fileEditor)
        panel.text = WARNING_TEXT
        return panel
    }
}
