package io.github.koollsl.lsl.preprocessor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LslDeadCodeEliminationTest : BasePlatformTestCase() {

    fun testStripUnusedLocalGlobalsAndFunctions() {
        val code = """
integer unusedVar = 10;
integer usedVar = 20;

integer unusedFunc(integer x) {
    return x * 2;
}

integer usedFunc(integer x) {
    return x + usedVar;
}

default {
    state_entry() {
        integer res = usedFunc(5);
        llOwnerSay((string)res);
    }
}
        """.trimIndent()

        val result = LslPreprocessorEngine.eliminateDeadCode(project, code)
        assertTrue(result.contains("usedVar"))
        assertTrue(result.contains("usedFunc"))
        assertTrue(result.contains("state_entry"))
        assertFalse(result.contains("unusedVar"))
        assertFalse(result.contains("unusedFunc"))
    }

    fun testTransitiveFunctionAndGlobalDependencies() {
        val code = """
integer depGlobal = 100;
integer unusedGlobal = 999;

integer leafFunc() {
    return depGlobal;
}

integer intermediateFunc() {
    return leafFunc() + 1;
}

integer unusedFunc() {
    return unusedGlobal;
}

default {
    state_entry() {
        integer v = intermediateFunc();
    }
}
        """.trimIndent()

        val result = LslPreprocessorEngine.eliminateDeadCode(project, code)
        assertTrue(result.contains("depGlobal"))
        assertTrue(result.contains("leafFunc"))
        assertTrue(result.contains("intermediateFunc"))
        assertFalse(result.contains("unusedGlobal"))
        assertFalse(result.contains("unusedFunc"))
    }

    fun testGlobalVariableShadowedLocally() {
        val code = """
integer shadowedGlobal = 50;

default {
    state_entry() {
        integer shadowedGlobal = 10;
        llOwnerSay((string)shadowedGlobal);
    }
}
        """.trimIndent()

        val result = LslPreprocessorEngine.eliminateDeadCode(project, code)
        assertFalse(result.contains("integer shadowedGlobal = 50;"))
    }

    fun testGlobalVariableInitializerReferencesAnotherGlobal() {
        val code = """
integer baseVar = 10;
integer derivedVar = baseVar;
integer unusedVar = 99;

default {
    state_entry() {
        llOwnerSay((string)derivedVar);
    }
}
        """.trimIndent()

        val result = LslPreprocessorEngine.eliminateDeadCode(project, code)
        assertTrue(result.contains("baseVar"))
        assertTrue(result.contains("derivedVar"))
        assertFalse(result.contains("unusedVar"))
    }

    fun testMultipleStatesTraceEventRoots() {
        val code = """
integer state1Var = 1;
integer state2Var = 2;
integer unusedVar = 3;

funcForState2() {
    llOwnerSay((string)state2Var);
}

default {
    state_entry() {
        llOwnerSay((string)state1Var);
        state running;
    }
}

state running {
    touch_start(integer total_number) {
        funcForState2();
    }
}
        """.trimIndent()

        val result = LslPreprocessorEngine.eliminateDeadCode(project, code)
        assertTrue(result.contains("state1Var"))
        assertTrue(result.contains("state2Var"))
        assertTrue(result.contains("funcForState2"))
        assertFalse(result.contains("unusedVar"))
    }

    fun testPreprocessFileWithIncludesAndDeadCode() {
        myFixture.addFileToProject(
            "Lib.lslp",
            """
integer LIB_UNUSED = 123;
integer LIB_USED = 456;

integer libUnusedFunc() {
    return LIB_UNUSED;
}

integer libUsedFunc() {
    return LIB_USED;
}
            """.trimIndent()
        )

        val mainFile = myFixture.addFileToProject(
            "Main.lslp",
            """
#include "Lib.lslp"

integer MAIN_VAR = 789;

default {
    state_entry() {
        integer res = libUsedFunc() + MAIN_VAR;
        llOwnerSay((string)res);
    }
}
            """.trimIndent()
        )

        val result = LslPreprocessorEngine.preprocessFile(mainFile)
        assertFalse(result.contains("integer LIB_UNUSED"))
        assertFalse(result.contains("integer LIB_USED"))
        assertFalse(result.contains("integer MAIN_VAR"))
        assertTrue(result.contains("libUsedFunc"))
        assertTrue(result.contains("456"))
        assertTrue(result.contains("789"))
        assertTrue(result.contains("state_entry"))
        assertFalse(result.contains("LIB_UNUSED"))
        assertFalse(result.contains("libUnusedFunc"))
    }

    fun testAllStatesEventHandlersAsRoots() {
        val code = """
integer reachableVarDefault = 1;
integer reachableVarCustom = 2;
integer unreachableVar = 3;

unreachableFunc() {
    llOwnerSay((string)unreachableVar);
}

reachableFuncCustom() {
    llOwnerSay((string)reachableVarCustom);
}

default {
    state_entry() {
        llOwnerSay((string)reachableVarDefault);
    }
}

state customState {
    touch_start(integer total_number) {
        reachableFuncCustom();
    }
}
        """.trimIndent()

        val result = LslPreprocessorEngine.eliminateDeadCode(project, code)
        assertTrue(result.contains("reachableVarDefault"))
        assertTrue(result.contains("reachableVarCustom"))
        assertTrue(result.contains("reachableFuncCustom"))
        assertTrue(result.contains("state customState"))
        assertFalse(result.contains("unreachableVar"))
        assertFalse(result.contains("unreachableFunc"))
    }

    fun testSaveLslpFileGeneratesBuildLsl() {
        val lslpFile = myFixture.addFileToProject(
            "src/MyScript.lslp",
            """
integer unusedGlobal = 100;
integer usedGlobal = 200;

integer helper() {
    return usedGlobal;
}

default {
    state_entry() {
        llOwnerSay((string)helper());
    }
}
            """.trimIndent()
        )

        LslPreprocessorEngine.processFileOnSave(lslpFile.virtualFile, project)

        val parentDir = lslpFile.virtualFile.parent
        val buildDir = parentDir.findChild("build")
        assertNotNull("build directory should be created", buildDir)

        val outputLsl = buildDir!!.findChild("MyScript.lsl")
        assertNotNull("MyScript.lsl output file should be generated", outputLsl)

        val outputText = String(outputLsl!!.contentsToByteArray())
        assertTrue(outputText.contains("helper"))
        assertTrue(outputText.contains("200"))
        assertFalse(outputText.contains("usedGlobal"))
        assertFalse(outputText.contains("unusedGlobal"))
    }

    fun testSaveLslmFileDoesNotGenerateStandaloneBuildLslm() {
        val lslmFile = myFixture.addFileToProject(
            "src/MyHeader.lslm",
            """
integer LIB_VAR = 42;
integer getLibVar() {
    return LIB_VAR;
}
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "src/Consumer.lslp",
            """
#include "MyHeader.lslm"

default {
    state_entry() {
        llOwnerSay((string)getLibVar());
    }
}
            """.trimIndent()
        )

        LslPreprocessorEngine.processFileOnSave(lslmFile.virtualFile, project)

        val parentDir = lslmFile.virtualFile.parent
        val buildDir = parentDir.findChild("build")
        assertNotNull("build directory should exist for consumer", buildDir)

        val lslmOutput = buildDir!!.findChild("MyHeader.lslm")
        val lslOutputForHeader = buildDir.findChild("MyHeader.lsl")
        assertNull("Should NOT generate MyHeader.lslm in build folder", lslmOutput)
        assertNull("Should NOT generate MyHeader.lsl for .lslm header file", lslOutputForHeader)

        val consumerOutput = buildDir.findChild("Consumer.lsl")
        assertNotNull("Consumer.lsl should be updated when lslm is saved", consumerOutput)
        val consumerText = String(consumerOutput!!.contentsToByteArray())
        assertFalse(consumerText.contains("integer LIB_VAR"))
        assertTrue(consumerText.contains("getLibVar"))
        assertTrue(consumerText.contains("42"))
    }

    fun testArtifactFilteringOnlyLslInBuild() {
        val lslpFile = myFixture.addFileToProject(
            "pkg/TestScript.lslp",
            """
default {
    state_entry() {
        llOwnerSay("hello");
    }
}
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "pkg/TestHeader.lslm",
            """
// header
            """.trimIndent()
        )

        LslPreprocessorEngine.processFileOnSave(lslpFile.virtualFile, project)

        val parentDir = lslpFile.virtualFile.parent
        val buildDir = parentDir.findChild("build")
        assertNotNull(buildDir)

        val children = buildDir!!.children
        assertTrue(children.all { it.extension?.lowercase() == "lsl" })
        assertNull(buildDir.findChild("TestScript.lslp"))
        assertNull(buildDir.findChild("TestHeader.lslm"))
    }

    fun testMultipleEventHandlersReachability() {
        val code = """
integer timerVar = 10;
integer touchVar = 20;
integer linkVar = 30;
integer unusedVar = 40;

timerFunc() {
    llOwnerSay((string)timerVar);
}

touchFunc() {
    llOwnerSay((string)touchVar);
}

linkFunc() {
    llOwnerSay((string)linkVar);
}

unusedFunc() {
    llOwnerSay((string)unusedVar);
}

default {
    timer() {
        timerFunc();
    }
    touch_start(integer total_number) {
        touchFunc();
    }
    link_message(integer sender_num, integer num, string str, key id) {
        linkFunc();
    }
}
        """.trimIndent()

        val result = LslPreprocessorEngine.eliminateDeadCode(project, code)
        assertTrue(result.contains("timerVar"))
        assertTrue(result.contains("touchVar"))
        assertTrue(result.contains("linkVar"))
        assertTrue(result.contains("timerFunc"))
        assertTrue(result.contains("touchFunc"))
        assertTrue(result.contains("linkFunc"))
        assertFalse(result.contains("unusedVar"))
        assertFalse(result.contains("unusedFunc"))
    }

    fun testPureInternalPreprocessingPipeline() {
        myFixture.addFileToProject(
            "include/MathLib.lslm",
            """
integer add(integer a, integer b) {
    return a + b;
}
integer unusedLibFunc() {
    return 999;
}
            """.trimIndent()
        )

        val lslpFile = myFixture.addFileToProject(
            "sub/TestScript.lslp",
            """
#include "include/MathLib.lslm"

integer scriptUnused = 123;

default {
    state_entry() {
        integer sum = add(10, 20);
        llOwnerSay((string)sum);
    }
}
            """.trimIndent()
        )

        val resultFile = LslPreprocessorEngine.processLslpFile(lslpFile.virtualFile, project)
        assertNotNull(resultFile)

        val outputText = String(resultFile!!.contentsToByteArray())
        assertTrue("Output should contain add function", outputText.contains("integer add("))
        assertTrue("Output should contain state_entry", outputText.contains("state_entry"))
        assertFalse("Output must NOT contain unusedLibFunc", outputText.contains("unusedLibFunc"))
        assertFalse("Output must NOT contain scriptUnused", outputText.contains("scriptUnused"))
        assertFalse("Output must NOT contain #include directive", outputText.contains("#include"))
    }

    fun testUserReportedDeadCodeElimination() {
        myFixture.addFileToProject(
            "Lib.lslm",
            """
// Library test Lib.lsm
string ProdA = "TEST A";
string ProdB = "TEST B";

LibHello( string s)
{
    llOwnerSay( "340 The Lib says: " + s);
}
            """.trimIndent()
        )

        val lslpFile = myFixture.addFileToProject(
            "Test.lslp",
            """
#include "Lib.lslm"
#undef TEST
string Maybe = "NO";

DisplayMaybe( string s)
{
	llOwnerSay(s);
}

default
{

	state_entry( )
	{
		string msg = "58 Hi there " + ProdA;

#ifdef TEST
		Display(msg);
#endif

		LibHello(msg);
	}
}
            """.trimIndent()
        )

        val resultFile = LslPreprocessorEngine.processLslpFile(lslpFile.virtualFile, project)
        assertNotNull(resultFile)

        val outputText = String(resultFile!!.contentsToByteArray())
        assertFalse(outputText.contains("ProdA"))
        assertTrue(outputText.contains("TEST A"))
        assertTrue(outputText.contains("LibHello"))
        assertFalse("Output must NOT contain ProdB", outputText.contains("ProdB"))
        assertFalse("Output must NOT contain Maybe", outputText.contains("Maybe"))
        assertFalse("Output must NOT contain DisplayMaybe", outputText.contains("DisplayMaybe"))
    }

    fun testHeaderBannerAndFileIncludeOriginMarkers() {
        myFixture.addFileToProject(
            "UsedLib.lslm",
            """
// Floating header comment in UsedLib (should be discarded)
// Copyright 2026

// Doc for usedFunction
integer usedFunction(integer x) {
    // Inside usedFunction body comment (should be retained)
    return x * 2;
}

// Doc for deadFunction in UsedLib (should be discarded)
integer deadFunction() {
    return 0;
}
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "UnusedLib.lslm",
            """
// Floating header in UnusedLib
integer completelyUnusedVar = 123;
integer completelyUnusedFunc() { return 456; }
            """.trimIndent()
        )

        val mainFile = myFixture.addFileToProject(
            "BannerScript.lslp",
            """
// Main root script top floating comment (should be preserved)
// Version 1.0.0

#include "UsedLib.lslm"
#include "UnusedLib.lslm"

// Doc for mainVar
integer mainVar = 10;

// Doc for deadMainVar (should be discarded)
integer deadMainVar = 99;

default {
    state_entry() {
        mainVar = mainVar + 1;
        integer res = usedFunction(mainVar);
        llOwnerSay((string)res);
    }
}
            """.trimIndent()
        )

        val resultFile = LslPreprocessorEngine.processLslpFile(mainFile.virtualFile, project)
        assertNotNull(resultFile)

        val outputText = String(resultFile!!.contentsToByteArray())
        val lines = outputText.lines()

        // 1. Line 1: Header Banner
        assertTrue(
            "Line 1 must start with '// Generated by LSL Preprocessor on ' followed by timestamp",
            lines[0].matches(Regex("""^// Generated by LSL Preprocessor on \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$"""))
        )
        assertEquals(
            "// Primary Source: BannerScript.lslp",
            lines[1]
        )

        // 2. Main root file top-level floating comments preserved
        assertTrue("Main floating comment preserved", outputText.contains("// Main root script top floating comment (should be preserved)"))
        assertTrue("Main floating comment version preserved", outputText.contains("// Version 1.0.0"))

        // 3. Included file origin markers
        assertTrue("Should contain Begin Include for UsedLib.lslm", outputText.contains("// --- Begin Include: UsedLib.lslm ---"))
        assertTrue("Should contain End Include for UsedLib.lslm", outputText.contains("// --- End Include: UsedLib.lslm ---"))
        assertFalse("Should NOT contain Include markers for UnusedLib.lslm", outputText.contains("UnusedLib.lslm"))

        // 4. Comment retention in includes
        assertFalse("Floating header in include must be discarded", outputText.contains("Floating header comment in UsedLib"))
        assertTrue("Doc comment for surviving func must be retained", outputText.contains("// Doc for usedFunction"))
        assertTrue("Inside body comment must be retained", outputText.contains("// Inside usedFunction body comment"))
        assertFalse("Dead func doc comment must be discarded", outputText.contains("Doc for deadFunction"))
        assertFalse("Dead func must be discarded", outputText.contains("deadFunction"))

        // 5. Main file comment retention
        assertTrue("Doc for surviving mainVar retained", outputText.contains("// Doc for mainVar"))
        assertTrue("Surviving mainVar retained", outputText.contains("integer mainVar = 10;"))
        assertFalse("Doc for dead mainVar discarded", outputText.contains("Doc for deadMainVar"))
        assertFalse("Dead mainVar discarded", outputText.contains("deadMainVar"))
    }

    fun testNestedIfdefAndConditionalIncludePipeline() {
        myFixture.addFileToProject(
            "FeatureLib.lslm",
            """
// FeatureLib header comment (discarded)

#ifdef ENABLE_FAST_MATH
// Fast add doc comment
integer fastAdd(integer a, integer b) {
    // fast addition
    return a + b;
}
#else
// Slow add doc comment
integer slowAdd(integer a, integer b) {
    return a + b;
}
#endif

// Unused feature func
integer unusedFeature() {
    return 42;
}
            """.trimIndent()
        )

        val mainFile = myFixture.addFileToProject(
            "NestedConditional.lslp",
            """
// Main Nested Conditional Test Script

#define ENABLE_FAST_MATH 1
#define DEBUG_MODE 1
#undef UNUSED_FLAG

#ifdef ENABLE_FAST_MATH
#include "FeatureLib.lslm"
#endif

#ifdef NEVER_DEFINED
#include "NonExistentLib.lslm"
integer neverCompiledVar = 999;
#endif

default {
    state_entry() {
#ifdef DEBUG_MODE
        integer result = fastAdd(10, 20);
        llOwnerSay((string)result);
#else
        llOwnerSay("disabled");
#endif
    }
}
            """.trimIndent()
        )

        val resultFile = LslPreprocessorEngine.processLslpFile(mainFile.virtualFile, project)
        assertNotNull(resultFile)

        val outputText = String(resultFile!!.contentsToByteArray())

        // Banner check
        assertTrue(outputText.contains("// Primary Source: NestedConditional.lslp"))

        // Active branch in include kept
        assertTrue("Should contain Begin Include: FeatureLib.lslm", outputText.contains("// --- Begin Include: FeatureLib.lslm ---"))
        assertTrue("Should contain End Include: FeatureLib.lslm", outputText.contains("// --- End Include: FeatureLib.lslm ---"))
        assertTrue("Should contain fastAdd", outputText.contains("integer fastAdd("))
        assertTrue("Should retain direct doc comment for fastAdd", outputText.contains("// Fast add doc comment"))
        assertTrue("Should retain body comment for fastAdd", outputText.contains("// fast addition"))

        // Inactive branch in include discarded
        assertFalse("Should NOT contain slowAdd", outputText.contains("slowAdd"))
        assertFalse("Should NOT contain slowAdd doc comment", outputText.contains("Slow add doc comment"))

        // Inactive branch in main discarded
        assertFalse("Should NOT contain neverCompiledVar", outputText.contains("neverCompiledVar"))
        assertFalse("Should NOT contain NonExistentLib", outputText.contains("NonExistentLib"))
        assertFalse("Should NOT contain disabled debug branch", outputText.contains("disabled"))

        // Directives discarded (no leftover #ifdef or #endif in output)
        assertFalse("Should NOT contain #ifdef", outputText.contains("#ifdef"))
        assertFalse("Should NOT contain #endif", outputText.contains("#endif"))
        assertFalse("Should NOT contain #define", outputText.contains("#define"))
        assertFalse("Should NOT contain #undef", outputText.contains("#undef"))

        // Dead functions discarded
        assertFalse("Should NOT contain unusedFeature", outputText.contains("unusedFeature"))
    }
}
