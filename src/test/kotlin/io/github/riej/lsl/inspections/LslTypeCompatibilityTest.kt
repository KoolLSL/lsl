package io.github.riej.lsl.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.riej.lsl.LslPrimitiveType
import io.github.riej.lsl.parser.LslTypes

class LslTypeCompatibilityTest : BasePlatformTestCase() {

    fun testPrimitiveTypeOperations() {
        // key = string
        assertEquals(LslPrimitiveType.KEY, LslPrimitiveType.KEY.operationTo(LslPrimitiveType.STRING, LslTypes.ASSIGN))
        // string = key
        assertEquals(LslPrimitiveType.STRING, LslPrimitiveType.STRING.operationTo(LslPrimitiveType.KEY, LslTypes.ASSIGN))

        // key == string, string == key
        assertEquals(LslPrimitiveType.INTEGER, LslPrimitiveType.KEY.operationTo(LslPrimitiveType.STRING, LslTypes.EQUAL))
        assertEquals(LslPrimitiveType.INTEGER, LslPrimitiveType.STRING.operationTo(LslPrimitiveType.KEY, LslTypes.EQUAL))

        // key != string, string != key
        assertEquals(LslPrimitiveType.INTEGER, LslPrimitiveType.KEY.operationTo(LslPrimitiveType.STRING, LslTypes.NOT_EQUAL))
        assertEquals(LslPrimitiveType.INTEGER, LslPrimitiveType.STRING.operationTo(LslPrimitiveType.KEY, LslTypes.NOT_EQUAL))

        // string + key, key + string
        assertEquals(LslPrimitiveType.STRING, LslPrimitiveType.STRING.operationTo(LslPrimitiveType.KEY, LslTypes.PLUS))
        assertEquals(LslPrimitiveType.STRING, LslPrimitiveType.KEY.operationTo(LslPrimitiveType.STRING, LslTypes.PLUS))

        // string += key, key += string
        assertEquals(LslPrimitiveType.STRING, LslPrimitiveType.STRING.operationTo(LslPrimitiveType.KEY, LslTypes.PLUS_ASSIGN))
        assertEquals(LslPrimitiveType.KEY, LslPrimitiveType.KEY.operationTo(LslPrimitiveType.STRING, LslTypes.PLUS_ASSIGN))
    }

    fun testAssignmentInspectionAcceptsKeyAndStringInterchangeably() {
        val file = myFixture.configureByText(
            "Test.lsl",
            """
key gKey = "some-string-uuid";
string gStr = (key)"some-key-uuid";

key testFunc(string s) {
    return s;
}

string testFunc2(key k) {
    return k;
}

default {
    state_entry() {
        key k = "local-string";
        string s = (key)"local-key";
        k = s;
        s = k;
        testFunc(k);
        testFunc2(s);
    }
}
            """.trimIndent()
        )

        val manager = InspectionManager.getInstance(project)

        val assignmentProblems = LslInvalidAssignmentTypeInspection().checkFile(file, manager, false)
        assertEquals(0, assignmentProblems.size)

        val returnProblems = LslInvalidReturnTypeInspection().checkFile(file, manager, false)
        assertEquals(0, returnProblems.size)

        val callProblems = LslInvalidFunctionCallArgumentInspection().checkFile(file, manager, false)
        assertEquals(0, callProblems.size)
    }
}
