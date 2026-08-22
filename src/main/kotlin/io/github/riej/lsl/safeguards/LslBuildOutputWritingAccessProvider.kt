package io.github.riej.lsl.safeguards

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider

class LslBuildOutputWritingAccessProvider(val project: Project) : WritingAccessProvider() {
    override fun isPotentiallyWritable(file: VirtualFile): Boolean {
        if (LslBuildOutputNotificationProvider.isGeneratedBuildFile(file)) {
            return false
        }
        return true
    }

    override fun requestWriting(files: Collection<VirtualFile>): Collection<VirtualFile> {
        return files.filter { LslBuildOutputNotificationProvider.isGeneratedBuildFile(it) }
    }
}
