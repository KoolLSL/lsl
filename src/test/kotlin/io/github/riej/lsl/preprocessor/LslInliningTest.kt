package io.github.riej.lsl.preprocessor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LslInliningTest : BasePlatformTestCase() {

    fun testGlobalVariableInlining() {
        val code = """
#inline string PREFIX = "LOG:";
#inline integer TIMEOUT = 30;

default {
    state_entry() {
        llOwnerSay(PREFIX + " ready");
        llSetTimerEvent(TIMEOUT);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse("Inlined global PREFIX should not be declared", result.contains("string PREFIX ="))
        assertFalse("Inlined global TIMEOUT should not be declared", result.contains("integer TIMEOUT ="))
        assertTrue("PREFIX should be substituted and folded with literal value", result.contains("llOwnerSay(\"LOG: ready\")"))
        assertTrue("TIMEOUT should be substituted with literal value", result.contains("llSetTimerEvent(30)"))
    }

    fun testGlobalVariableShadowedLocally() {
        val code = """
#inline integer VALUE = 100;

default {
    state_entry() {
        integer VALUE = 5;
        llOwnerSay((string)VALUE);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse("Inlined global VALUE definition should be stripped", result.contains("integer VALUE = 100;"))
        assertTrue("Local VALUE definition should be kept", result.contains("integer VALUE = 5;"))
        assertTrue("Local reference to VALUE should not be replaced by 100", result.contains("llOwnerSay((string)VALUE);"))
    }

    fun testSingleExpressionFunctionInlining() {
        val code = """
#inline integer add(integer a, integer b) {
    return a + b;
}

default {
    state_entry() {
        integer x = add(1, 2);
        integer y = add(x + 1, 4) * 3;
        llOwnerSay((string)x);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse("Function declaration should not be emitted", result.contains("integer add("))
        assertTrue("Single expression call should expand and fold directly", result.contains("integer x = 3;"))
        assertTrue("Complex argument call should preserve precedence", result.contains("integer y = ((x + 1) + 4) * 3;"))
    }

    fun testSingleLineInlineDirectiveRecognition() {
        val code = """
#inline integer multiply(integer a, integer b) { return a * b; }
#inline string TAG = "[TEST]";

default {
    state_entry() {
        integer res = multiply(3, 7);
        llOwnerSay(TAG + (string)res);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse(result.contains("integer multiply("))
        assertFalse(result.contains("string TAG ="))
        assertTrue(result.contains("integer res = 21;"))
        assertTrue(result.contains("llOwnerSay(\"[TEST]\" + (string)res);"))
    }

    fun testBlockFunctionInliningWithAlphaRenaming() {
        val code = """
#inline integer computeSum(integer n) {
    integer total = 0;
    integer i;
    for (i = 1; i <= n; i++) {
        total += i;
    }
    return total;
}

default {
    state_entry() {
        integer total = 999;
        integer res = computeSum(5);
        llOwnerSay((string)res);
        llOwnerSay((string)total);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse("Function computeSum should not be declared", result.contains("integer computeSum("))
        assertTrue("Parameter n should be renamed uniquely", result.contains("__inline_computeSum_n_1"))
        assertTrue("Local total should be renamed uniquely to avoid collision", result.contains("__inline_computeSum_total_1"))
        assertTrue("Local i should be renamed uniquely", result.contains("__inline_computeSum_i_1"))
        assertTrue("Return variable should be defined", result.contains("__inline_computeSum_ret_1"))
        assertTrue("Caller's total variable must be preserved", result.contains("integer total = 999;"))
    }

    fun testMultipleCallSitesHaveUniqueAlphaRenaming() {
        val code = """
#inline integer doubleVal(integer x) {
    integer temp = x * 2;
    return temp;
}

default {
    state_entry() {
        integer a = doubleVal(5);
        integer b = doubleVal(10);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertTrue(result.contains("__inline_doubleVal_x_1"))
        assertTrue(result.contains("__inline_doubleVal_temp_1"))
        assertTrue(result.contains("__inline_doubleVal_x_2"))
        assertTrue(result.contains("__inline_doubleVal_temp_2"))
    }

    fun testVoidBlockFunctionInlining() {
        val code = """
#inline logMessage(string prefix, string msg) {
    string formatted = prefix + " : " + msg;
    llOwnerSay(formatted);
}

default {
    state_entry() {
        logMessage("WARN", "Disk full");
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse(result.contains("logMessage(string"))
        assertTrue(result.contains("__inline_logMessage_prefix_1 = \"WARN\";"))
        assertTrue(result.contains("__inline_logMessage_msg_1 = \"Disk full\";"))
        assertTrue(result.contains("string __inline_logMessage_formatted_1 = __inline_logMessage_prefix_1 + \" : \" + __inline_logMessage_msg_1;"))
        assertTrue(result.contains("llOwnerSay(__inline_logMessage_formatted_1);"))
    }

    fun testRecursiveFunctionInliningRejection() {
        val code = """
#inline integer factorial(integer n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

default {
    state_entry() {
        integer f = factorial(5);
        llOwnerSay((string)f);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertTrue("Recursive function should be kept as standard function", result.contains("integer factorial(integer n)"))
        assertTrue("Call to factorial should remain intact", result.contains("factorial(5)"))
    }

    fun testIndirectRecursiveFunctionInliningRejection() {
        val code = """
#inline integer funcA(integer n) {
    if (n <= 0) return 0;
    return funcB(n - 1);
}

#inline integer funcB(integer n) {
    if (n <= 0) return 0;
    return funcA(n - 1);
}

default {
    state_entry() {
        integer res = funcA(3);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertTrue("Mutual recursive funcA should be retained", result.contains("integer funcA("))
        assertTrue("Mutual recursive funcB should be retained", result.contains("integer funcB("))
    }

    fun testInlinedFunctionInIncludeFile() {
        myFixture.addFileToProject("math_utils.lslm", """
#inline integer square(integer x) {
    return x * x;
}
#inline string MODULE_NAME = "MathLib";
        """.trimIndent())

        val mainCode = """
#include "math_utils.lslm"

default {
    state_entry() {
        llOwnerSay(MODULE_NAME);
        integer sq = square(6);
        llOwnerSay((string)sq);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("main.lslp", mainCode)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse("square function should not be declared", result.contains("integer square("))
        assertFalse("MODULE_NAME should not be declared", result.contains("string MODULE_NAME ="))
        assertTrue("MODULE_NAME should be inlined", result.contains("llOwnerSay(\"MathLib\")"))
        assertTrue("square should be inlined and folded", result.contains("integer sq = 36;"))
    }

    fun testNestedInliningOfFunctionsAndGlobals() {
        val code = """
#inline string TAG = "[MATH]";
#inline integer square(integer x) {
    return x * x;
}
#inline integer sumSquares(integer a, integer b) {
    return square(a) + square(b);
}

default {
    state_entry() {
        llOwnerSay(TAG);
        integer ans = sumSquares(3, 4);
        llOwnerSay((string)ans);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse(result.contains("TAG ="))
        assertFalse(result.contains("square("))
        assertFalse(result.contains("sumSquares("))
        assertTrue(result.contains("llOwnerSay(\"[MATH]\");"))
        assertTrue("Unexpected result:\n$result", result.contains("integer ans = 25;"))
    }

    fun testBlockFunctionWithBranchingAndMultipleReturns() {
        val code = """
#inline integer maxVal(integer a, integer b) {
    if (a > b) {
        return a;
    }
    return b;
}

default {
    state_entry() {
        integer m = maxVal(10, 20);
        llOwnerSay((string)m);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse(result.contains("integer maxVal("))
        assertTrue(result.contains("__inline_maxVal_a_1 = 10;"))
        assertTrue(result.contains("__inline_maxVal_b_1 = 20;"))
        assertTrue(result.contains("__inline_maxVal_ret_1"))
        assertTrue(result.contains("jump __inline_maxVal_end_1;"))
        assertTrue(result.contains("@__inline_maxVal_end_1;"))
        assertTrue(result.contains("integer m = __inline_maxVal_ret_1;"))
    }

    fun testGlobalVariableVectorInlining() {
        val code = """
#inline vector OFFSET = <1.0, 2.0, 3.0>;

default {
    state_entry() {
        vector v = OFFSET;
        llOwnerSay((string)v);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse(result.contains("vector OFFSET ="))
        assertTrue(result.contains("vector v = <1.0, 2.0, 3.0>;"))
    }

    fun testDeadCodeEliminationWithInlining() {
        val code = """
#inline integer inlinedHelper(integer a) {
    return a * 2;
}

integer normalHelper(integer a) {
    return a + 10;
}

integer unusedFunc() {
    return 999;
}

default {
    state_entry() {
        integer x = inlinedHelper(5);
        integer y = normalHelper(x);
        llOwnerSay((string)y);
    }
}
        """.trimIndent()

        val psiFile = myFixture.configureByText("test.lslp", code)
        val result = LslPreprocessorEngine.preprocess(psiFile)

        assertFalse("inlinedHelper should not be emitted", result.contains("integer inlinedHelper("))
        assertTrue("normalHelper should be retained because it is used", result.contains("integer normalHelper(integer a)"))
        assertFalse("unusedFunc should be eliminated by DCE", result.contains("integer unusedFunc("))
        assertTrue(result.contains("integer x = 10;"))
    }
}
