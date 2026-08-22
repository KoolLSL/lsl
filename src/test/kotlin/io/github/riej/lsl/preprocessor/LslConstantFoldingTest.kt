package io.github.riej.lsl.preprocessor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LslConstantFoldingTest : BasePlatformTestCase() {

    fun testBitwiseMaskConstantFoldingAndPropagation() {
        val code = """
integer BIT8 = 256;
integer BIT9 = 512;
integer LOOP_BB = 256;
integer LOOP_CHAT = 512;

default {
    state_entry() {
        integer op = 768;
        if (op & (LOOP_BB | LOOP_CHAT)) {
            llOwnerSay("Mask match");
        }
        if (op & BIT8) {
            llOwnerSay("BIT8 match");
        }
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse("BIT8 declaration should be removed by DCE", result.contains("integer BIT8"))
        assertFalse("BIT9 declaration should be removed by DCE", result.contains("integer BIT9"))
        assertFalse("LOOP_BB declaration should be removed by DCE", result.contains("integer LOOP_BB"))
        assertFalse("LOOP_CHAT declaration should be removed by DCE", result.contains("integer LOOP_CHAT"))
        assertTrue("Subexpression (LOOP_BB | LOOP_CHAT) should be folded to 768", result.contains("if (op & 768)"))
        assertTrue("Reference BIT8 should be substituted with 256", result.contains("if (op & 256)"))
    }

    fun testArithmeticAndBitwiseOperatorsFolding() {
        val code = """
integer CONST_X = 10;
integer CONST_Y = 20;
float CONST_F = 15.0;
string CONST_S = "Hello, ";

default {
    state_entry() {
        integer a = CONST_X + CONST_Y * 3;
        integer b = (1 << 4) | (1 << 5);
        integer c = ~0 & 255;
        float f = CONST_F / 2.0;
        string s = CONST_S + "World!";

        llOwnerSay((string)a);
        llOwnerSay((string)b);
        llOwnerSay((string)c);
        llOwnerSay((string)f);
        llOwnerSay(s);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse(result.contains("integer CONST_X"))
        assertFalse(result.contains("integer CONST_Y"))
        assertFalse(result.contains("float CONST_F"))
        assertFalse(result.contains("string CONST_S"))

        assertTrue("a (10 + 60) should fold to 70", result.contains("integer a = 70;"))
        assertTrue("b (16 | 32) should fold to 48", result.contains("integer b = 48;"))
        assertTrue("c (~0 & 255) should fold to 255", result.contains("integer c = 255;"))
        assertTrue("f (15.0 / 2.0) should fold to 7.5", result.contains("float f = 7.5;"))
        assertTrue("s string concatenation should fold to \"Hello, World!\"", result.contains("string s = \"Hello, World!\";"))
    }

    fun testChainedConstantDependencies() {
        val code = """
integer CONST_A = 100;
integer CONST_B = CONST_A;
integer CONST_C = CONST_B;

default {
    state_entry() {
        integer val = CONST_C;
        llOwnerSay((string)val);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse(result.contains("integer CONST_A"))
        assertFalse(result.contains("integer CONST_B"))
        assertFalse(result.contains("integer CONST_C"))
        assertTrue("Chained constant CONST_C should evaluate to 100", result.contains("integer val = 100;"))
    }

    fun testMutatedGlobalVariableNotTreatedAsConstant() {
        val code = """
integer constVar = 100;
integer mutatedVar = 200;

default {
    state_entry() {
        mutatedVar = mutatedVar + constVar;
        llOwnerSay((string)mutatedVar);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse("constVar should be folded and removed", result.contains("integer constVar"))
        assertTrue("mutatedVar must be preserved as global variable", result.contains("integer mutatedVar = 200;"))
        assertTrue("constVar reference should be substituted with 100", result.contains("mutatedVar = mutatedVar + 100;"))
    }

    fun testMutatedViaPrefixPostfixNotTreatedAsConstant() {
        val code = """
integer counterA = 0;
integer counterB = 10;
integer staticVal = 50;

default {
    state_entry() {
        counterA++;
        --counterB;
        integer sum = counterA + counterB + staticVal;
        llOwnerSay((string)sum);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse("staticVal should be removed", result.contains("integer staticVal"))
        assertTrue("counterA must remain global", result.contains("integer counterA = 0;"))
        assertTrue("counterB must remain global", result.contains("integer counterB = 10;"))
        assertTrue("staticVal reference replaced with 50", result.contains("counterA + counterB + 50"))
    }

    fun testLocalShadowingDoesNotAffectGlobalConstant() {
        val code = """
integer GLOBAL_VAL = 42;

default {
    state_entry() {
        integer GLOBAL_VAL = 99;
        GLOBAL_VAL = GLOBAL_VAL + 1;
        llOwnerSay((string)GLOBAL_VAL);
    }
    touch_start(integer total_number) {
        llOwnerSay((string)GLOBAL_VAL);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse("GLOBAL_VAL declaration should be removed by DCE", result.contains("integer GLOBAL_VAL = 42;"))
        assertTrue("Local shadowed variable must be preserved", result.contains("integer GLOBAL_VAL = 99;"))
        assertTrue("Local mutation preserved", result.contains("GLOBAL_VAL = GLOBAL_VAL + 1;"))
        assertTrue("Unshadowed reference in touch_start should be substituted and folded to \"42\"", result.contains("llOwnerSay(\"42\");"))
    }

    fun testIncludeFileWithBitwiseConstantsCompletelySubstituted() {
        myFixture.addFileToProject(
            "Bitmasks.lslm",
            """
// Bitmask definitions header
integer MASK_READ = 1;
integer MASK_WRITE = 2;
integer MASK_EXEC = 4;
            """.trimIndent()
        )

        val mainFile = myFixture.addFileToProject(
            "MainScript.lslp",
            """
#include "Bitmasks.lslm"

default {
    state_entry() {
        integer permissions = 5;
        if (permissions & (MASK_READ | MASK_WRITE | MASK_EXEC)) {
            llOwnerSay("Has permissions");
        }
        if (permissions & (MASK_READ | MASK_WRITE)) {
            llOwnerSay("Read or write");
        }
    }
}
            """.trimIndent()
        )

        val resultFile = LslPreprocessorEngine.processLslpFile(mainFile.virtualFile, project)
        assertNotNull(resultFile)

        val outputText = String(resultFile!!.contentsToByteArray())

        assertFalse("MASK_READ declaration should be removed", outputText.contains("MASK_READ"))
        assertFalse("MASK_WRITE declaration should be removed", outputText.contains("MASK_WRITE"))
        assertFalse("MASK_EXEC declaration should be removed", outputText.contains("MASK_EXEC"))
        assertFalse("Empty include header should be discarded", outputText.contains("Bitmasks.lslm"))

        assertTrue("MASK_READ | MASK_WRITE | MASK_EXEC should fold to 7", outputText.contains("if (permissions & 7)"))
        assertTrue("(MASK_READ | MASK_WRITE) should fold to 3", outputText.contains("if (permissions & 3)"))
    }
}
