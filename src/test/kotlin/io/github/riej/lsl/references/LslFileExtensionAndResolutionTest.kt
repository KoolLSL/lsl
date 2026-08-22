package io.github.riej.lsl.references

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.riej.lsl.LslFileType
import io.github.riej.lsl.psi.LslExpressionFunctionCall
import io.github.riej.lsl.psi.LslFunction
import io.github.riej.lsl.psi.LslGlobalVariable
import io.github.riej.lsl.psi.LslLValue

class LslFileExtensionAndResolutionTest : BasePlatformTestCase() {

    fun testDefaultFileExtensionIsLslp() {
        assertEquals("lslp", LslFileType.INSTANCE.defaultExtension)
    }

    fun testResolveFunctionAcrossLslpAndLslmFiles() {
        myFixture.addFileToProject(
            "MathLib.lslp",
            """
integer addNumbers(integer a, integer b) {
    return a + b;
}
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "LegacyLib.lslm",
            """
integer multiplyNumbers(integer a, integer b) {
    return a * b;
}
            """.trimIndent()
        )

        val mainFile = myFixture.configureByText(
            "Main.lslp",
            """
default {
    state_entry() {
        integer sum = addNumbers(1, 2);
        integer prod = multiplyNumbers(3, 4);
    }
}
            """.trimIndent()
        )

        val calls = com.intellij.psi.util.PsiTreeUtil.collectElementsOfType(
            mainFile,
            LslExpressionFunctionCall::class.java
        )

        val addCall = calls.first { it.functionName == "addNumbers" }
        val multiplyCall = calls.first { it.functionName == "multiplyNumbers" }

        val resolvedAdd = addCall.reference?.resolve()
        assertNotNull(resolvedAdd)
        assertTrue(resolvedAdd is LslFunction)
        assertEquals("addNumbers", (resolvedAdd as LslFunction).name)

        val resolvedMultiply = multiplyCall.reference?.resolve()
        assertNotNull(resolvedMultiply)
        assertTrue(resolvedMultiply is LslFunction)
        assertEquals("multiplyNumbers", (resolvedMultiply as LslFunction).name)
    }

    fun testResolveGlobalVariablesAcrossLslpAndLslmFiles() {
        myFixture.addFileToProject(
            "Config.lslp",
            """
integer CONFIG_TIMEOUT = 30;
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "LegacyConfig.lslm",
            """
string CONFIG_PREFIX = "TEST_";
            """.trimIndent()
        )

        val mainFile = myFixture.configureByText(
            "Consumer.lsl",
            """
default {
    state_entry() {
        integer t = CONFIG_TIMEOUT;
        string p = CONFIG_PREFIX;
    }
}
            """.trimIndent()
        )

        val lValues = com.intellij.psi.util.PsiTreeUtil.collectElementsOfType(
            mainFile,
            LslLValue::class.java
        )

        val timeoutLValue = lValues.first { it.variableName == "CONFIG_TIMEOUT" }
        val prefixLValue = lValues.first { it.variableName == "CONFIG_PREFIX" }

        val resolvedTimeout = timeoutLValue.reference?.resolve()
        assertNotNull(resolvedTimeout)
        assertTrue(resolvedTimeout is LslGlobalVariable)
        assertEquals("CONFIG_TIMEOUT", (resolvedTimeout as LslGlobalVariable).name)

        val resolvedPrefix = prefixLValue.reference?.resolve()
        assertNotNull(resolvedPrefix)
        assertTrue(resolvedPrefix is LslGlobalVariable)
        assertEquals("CONFIG_PREFIX", (resolvedPrefix as LslGlobalVariable).name)
    }

    fun testKwdbBuiltinsLoaded() {
        val kwdb = io.github.riej.lsl.KwdbData.getInstance(project)
        assertNotNull(kwdb.constants["NAK"])
        assertNotNull(kwdb.constants["EOF"])
        assertNotNull(kwdb.functions["llSay"])
        assertNotNull(kwdb.events["state_entry"])
    }
}
