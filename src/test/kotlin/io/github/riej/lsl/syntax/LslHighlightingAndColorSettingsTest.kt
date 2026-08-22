package io.github.riej.lsl.syntax

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.riej.lsl.LslLanguage

class LslHighlightingAndColorSettingsTest : BasePlatformTestCase() {

    fun testColorSettingsPageProperties() {
        val page = LslColorSettingsPage()

        assertEquals(LslLanguage.INSTANCE.displayName, page.displayName)
        assertNotNull(page.icon)
        assertNotNull(page.highlighter)
        assertTrue(page.highlighter is LslSyntaxHighlighter)

        val descriptors = page.attributeDescriptors
        assertTrue(descriptors.isNotEmpty())

        val descriptorKeys = descriptors.map { it.key }.toSet()
        assertTrue(descriptorKeys.contains(LslColorKeys.KEYWORD))
        assertTrue(descriptorKeys.contains(LslColorKeys.TYPE))
        assertTrue(descriptorKeys.contains(LslColorKeys.BUILTIN_FUNCTION))
        assertTrue(descriptorKeys.contains(LslColorKeys.BUILTIN_CONSTANT))
        assertTrue(descriptorKeys.contains(LslColorKeys.EVENT))

        val tagMap = page.additionalHighlightingTagToDescriptorMap
        assertNotNull(tagMap)
        assertTrue(tagMap.containsKey("builtin_function"))
        assertTrue(tagMap.containsKey("builtin_constant"))
        assertTrue(tagMap.containsKey("event"))
        assertEquals(LslColorKeys.BUILTIN_FUNCTION, tagMap["builtin_function"])
        assertEquals(LslColorKeys.BUILTIN_CONSTANT, tagMap["builtin_constant"])
        assertEquals(LslColorKeys.EVENT, tagMap["event"])

        val demoText = page.demoText
        assertTrue(demoText.contains("llOwnerSay"))
        assertTrue(demoText.contains("ZERO_VECTOR"))
        assertTrue(demoText.contains("state_entry"))
        assertTrue(demoText.contains("touch_start"))
    }

    fun testSemanticHighlightingForKwdbSymbols() {
        myFixture.configureByText(
            "TestHighlighting.lsl",
            """
            vector gPos = ZERO_VECTOR;
            integer gVal = TRUE;

            userFunc() {
            }

            default {
                state_entry() {
                    llOwnerSay("Hello");
                    llGetPos();
                    userFunc();
                }

                touch_start(integer total_number) {
                    llSay(0, "Touched");
                }
            }
            """.trimIndent()
        )

        val highlights = myFixture.doHighlighting()
        val textAttributesByText = highlights
            .filter { it.forcedTextAttributesKey != null }
            .associate { it.text to it.forcedTextAttributesKey }

        assertEquals(LslColorKeys.BUILTIN_CONSTANT, textAttributesByText["ZERO_VECTOR"])
        assertEquals(LslColorKeys.BUILTIN_CONSTANT, textAttributesByText["TRUE"])
        assertEquals(LslColorKeys.BUILTIN_FUNCTION, textAttributesByText["llOwnerSay"])
        assertEquals(LslColorKeys.BUILTIN_FUNCTION, textAttributesByText["llGetPos"])
        assertEquals(LslColorKeys.BUILTIN_FUNCTION, textAttributesByText["llSay"])
        assertEquals(LslColorKeys.EVENT, textAttributesByText["state_entry"])
        assertEquals(LslColorKeys.EVENT, textAttributesByText["touch_start"])

        // User function should not be highlighted as a builtin function
        assertNull(textAttributesByText["userFunc"])
    }

    fun testDisabledCodeNotAnnotated() {
        myFixture.configureByText(
            "TestDisabled.lslp",
            """
            #if 0
            default {
                state_entry() {
                    llOwnerSay(ZERO_VECTOR);
                }
            }
            #endif
            """.trimIndent()
        )

        val highlights = myFixture.doHighlighting()
        val textAttributesByText = highlights
            .filter { it.forcedTextAttributesKey == LslColorKeys.BUILTIN_FUNCTION || it.forcedTextAttributesKey == LslColorKeys.BUILTIN_CONSTANT || it.forcedTextAttributesKey == LslColorKeys.EVENT }
            .associate { it.text to it.forcedTextAttributesKey }

        assertTrue(textAttributesByText.isEmpty())
    }

    fun testShadowedSymbolsNotAnnotatedAsBuiltin() {
        myFixture.configureByText(
            "TestShadowed.lsl",
            """
            llOwnerSay(string msg) {
            }

            default {
                state_entry() {
                    integer ZERO_VECTOR = 5;
                    llOwnerSay((string)ZERO_VECTOR);
                }
            }
            """.trimIndent()
        )

        val highlights = myFixture.doHighlighting()
        val textAttributesByText = highlights
            .filter { it.forcedTextAttributesKey != null }
            .associate { it.text to it.forcedTextAttributesKey }

        // Local function llOwnerSay and local variable ZERO_VECTOR should not get builtin attributes
        assertNull(textAttributesByText["ZERO_VECTOR"])
        assertNull(textAttributesByText["llOwnerSay"])
        assertEquals(LslColorKeys.EVENT, textAttributesByText["state_entry"])
    }
}
