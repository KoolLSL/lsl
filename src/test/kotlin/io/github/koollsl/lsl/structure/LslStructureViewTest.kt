package io.github.koollsl.lsl.structure

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.koollsl.lsl.LslIcons
import io.github.koollsl.lsl.psi.*

class LslStructureViewTest : BasePlatformTestCase() {

    fun testStructureViewPresentationsAndIcons() {
        val file = myFixture.configureByText(
            "TestScript.lsl",
            """
integer gCount = 0;

myFunc(string msg, key id) {
}

default {
    state_entry() {
    }
}

state otherState {
    touch_start(integer total_number) {
    }
}
            """.trimIndent()
        ) as LslFile

        val rootElement = LslStructureViewElement(file)
        assertEquals("TestScript.lsl", rootElement.presentation.presentableText)
        assertEquals(LslIcons.FILE, rootElement.presentation.getIcon(false))

        val children = rootElement.children
        assertEquals(4, children.size)

        // Global variable
        val varElem = children[0] as LslStructureViewElement
        assertTrue(varElem.value is LslGlobalVariable)
        assertEquals("integer gCount", varElem.presentation.presentableText)
        assertNotNull(varElem.presentation.getIcon(false))

        // Function
        val funcElem = children[1] as LslStructureViewElement
        assertTrue(funcElem.value is LslFunction)
        assertEquals("void myFunc(string msg, key id)", funcElem.presentation.presentableText)
        assertNotNull(funcElem.presentation.getIcon(false))

        // Default state
        val defaultStateElem = children[2] as LslStructureViewElement
        assertTrue(defaultStateElem.value is LslStateDefault)
        assertEquals("default", defaultStateElem.presentation.presentableText)
        assertEquals(LslIcons.STATE, defaultStateElem.presentation.getIcon(false))

        // Default state children
        val defaultChildren = defaultStateElem.children
        assertEquals(1, defaultChildren.size)
        val eventElem1 = defaultChildren[0] as LslStructureViewElement
        assertTrue(eventElem1.value is LslEvent)
        assertEquals("state_entry()", eventElem1.presentation.presentableText)
        assertEquals(LslIcons.EVENT, eventElem1.presentation.getIcon(false))

        // Custom state
        val customStateElem = children[3] as LslStructureViewElement
        assertTrue(customStateElem.value is LslStateCustom)
        assertEquals("state otherState", customStateElem.presentation.presentableText)
        assertEquals(LslIcons.STATE, customStateElem.presentation.getIcon(false))

        // Custom state children
        val customChildren = customStateElem.children
        assertEquals(1, customChildren.size)
        val eventElem2 = customChildren[0] as LslStructureViewElement
        assertTrue(eventElem2.value is LslEvent)
        assertEquals("touch_start(integer total_number)", eventElem2.presentation.presentableText)
        assertEquals(LslIcons.EVENT, eventElem2.presentation.getIcon(false))
    }
}
