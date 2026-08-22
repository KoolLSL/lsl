package io.github.riej.lsl.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.riej.lsl.annotation.LslDisabledCodeInspectionSuppressor
import io.github.riej.lsl.psi.LslNamedElement
import com.intellij.psi.util.PsiTreeUtil

class LslInspectionSuppressionTest : BasePlatformTestCase() {

    fun testDisabledCodeInspectionSuppressor() {
        val file = myFixture.configureByText(
            "Test.lslp",
            """
#if 0
integer a = 1;
integer b = 2;
#endif
integer c = 3;

default {
}
            """.trimIndent()
        )

        val elements = PsiTreeUtil.collectElementsOfType(file, LslNamedElement::class.java).filter { it !is io.github.riej.lsl.psi.LslStateDefault }.toList()
        assertEquals(3, elements.size)

        val suppressor = LslDisabledCodeInspectionSuppressor()
        assertTrue(suppressor.isSuppressedFor(elements[0], "Redeclared identifier"))
        assertTrue(suppressor.isSuppressedFor(elements[1], "Redeclared identifier"))
        assertFalse(suppressor.isSuppressedFor(elements[2], "Redeclared identifier"))
    }

    fun testRedeclaredIdentifierInspectionIgnoresDisabledCode() {
        val file = myFixture.configureByText(
            "Test.lslp",
            """
#if 0
integer duplicateVar = 1;
#endif
integer duplicateVar = 2;
            """.trimIndent()
        )

        val inspection = LslRedeclaredIdentifierInspection()
        val manager = InspectionManager.getInstance(project)
        val problems = inspection.checkFile(file, manager, false)

        assertEquals(0, problems.size)
    }

    fun testRedeclaredIdentifierInspectionCatchesRealDuplicates() {
        val file = myFixture.configureByText(
            "Test.lslp",
            """
integer duplicateVar = 1;
integer duplicateVar = 2;
            """.trimIndent()
        )

        val inspection = LslRedeclaredIdentifierInspection()
        val manager = InspectionManager.getInstance(project)
        val problems = inspection.checkFile(file, manager, false)

        assertEquals(1, problems.size)
    }
}
