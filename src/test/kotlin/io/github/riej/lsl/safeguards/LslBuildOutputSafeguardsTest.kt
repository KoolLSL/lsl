package io.github.riej.lsl.safeguards

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LslBuildOutputSafeguardsTest : BasePlatformTestCase() {

    fun testIsGeneratedBuildLslFile() {
        val buildFile = LightVirtualFile("build/output.lsl", "default { state_entry() {} }")
        val outFile = LightVirtualFile("out/compiled.lsl", "default { state_entry() {} }")
        val outputDirFile = LightVirtualFile("output/script.lsl", "default { state_entry() {} }")
        val distFile = LightVirtualFile("dist/script.lsl", "default { state_entry() {} }")
        val srcFile = LightVirtualFile("src/main.lsl", "default { state_entry() {} }")
        val lslpFile = LightVirtualFile("build/script.lslp", "default { state_entry() {} }")
        val lslmFile = LightVirtualFile("out/lib.lslm", "default { state_entry() {} }")

        assertTrue(LslBuildOutputNotificationProvider.isGeneratedBuildFile(buildFile))
        assertTrue(LslBuildOutputNotificationProvider.isGeneratedBuildFile(outFile))
        assertTrue(LslBuildOutputNotificationProvider.isGeneratedBuildFile(outputDirFile))
        assertTrue(LslBuildOutputNotificationProvider.isGeneratedBuildFile(distFile))
        assertTrue(LslBuildOutputNotificationProvider.isGeneratedBuildFile(lslpFile))
        assertTrue(LslBuildOutputNotificationProvider.isGeneratedBuildFile(lslmFile))

        assertFalse(LslBuildOutputNotificationProvider.isGeneratedBuildFile(srcFile))
        assertFalse(LslBuildOutputNotificationProvider.isGeneratedBuildFile(null))

        assertTrue(LslBuildOutputNotificationProvider.isGeneratedBuildLslFile(buildFile))
        assertTrue(LslBuildOutputNotificationProvider.isGeneratedBuildLslFile(lslpFile))
        assertFalse(LslBuildOutputNotificationProvider.isGeneratedBuildLslFile(srcFile))
    }

    fun testWritingAccessProvider() {
        val provider = LslBuildOutputWritingAccessProvider(project)
        val buildFile = LightVirtualFile("build/main.lsl", "default { state_entry() {} }")
        val srcFile = LightVirtualFile("src/main.lsl", "default { state_entry() {} }")

        assertFalse(provider.isPotentiallyWritable(buildFile))
        assertTrue(provider.isPotentiallyWritable(srcFile))

        val readOnlyFiles = provider.requestWriting(listOf(buildFile, srcFile))
        assertEquals(listOf(buildFile), readOnlyFiles)
    }

    fun testNotificationMessage() {
        assertEquals(
            "This is a generated build file. Direct changes will be overwritten during preprocessing.",
            LslBuildOutputNotificationProvider.WARNING_TEXT
        )
    }
}
