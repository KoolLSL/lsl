package io.github.koollsl.lsl.formatting

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LslFormattingTest : BasePlatformTestCase() {

    private fun doTest(before: String, after: String) {
        val file = myFixture.configureByText("test.lsl", before.trimIndent())
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(file)
        }
        myFixture.checkResult(after.trimIndent())
    }

    fun testBasicFormatting() {
        doTest(
            """
default{
state_entry(){
llSay(0,"Hello");
}
}
            """,
            """
default
{
    state_entry()
    {
        llSay(0, "Hello");
    }
}
            """
        )
    }

    fun testGlobalsAndFunctions() {
        doTest(
            """
integer gCount=10;
string gName="test";
vector gPos=<1.0,2.0,3.0>;
list gList=[1,2,"three"];

integer add(integer a,integer b){
return a+b;
}

default{
state_entry(){
}
}
            """,
            """
integer gCount = 10;
string gName = "test";
vector gPos = <1.0, 2.0, 3.0>;
list gList = [1, 2, "three"];

integer add(integer a, integer b)
{
    return a + b;
}

default
{
    state_entry()
    {
    }
}
            """
        )
    }

    fun testControlFlowStatements() {
        doTest(
            """
default{
state_entry(){
integer i=0;
if(i>0){
llOwnerSay("positive");
}else{
llOwnerSay("non-positive");
}

while(i<5){
i++;
}

do{
i--;
}while(i>0);

for(i=0;i<10;i++){
llOwnerSay((string)i);
}
}
}
            """,
            """
default
{
    state_entry()
    {
        integer i = 0;
        if (i > 0)
        {
            llOwnerSay("positive");
        }
        else
        {
            llOwnerSay("non-positive");
        }

        while (i < 5)
        {
            i++;
        }

        do
        {
            i--;
        } while (i > 0);

        for (i = 0; i < 10; i++)
        {
            llOwnerSay((string)i);
        }
    }
}
            """
        )
    }

    fun testPreprocessorDirectives() {
        doTest(
            """
#define MAX_ITEMS 100
#include "library.lsl"

default{
state_entry(){
#ifdef DEBUG
llOwnerSay("debug");
#endif
llSay(0,"done");
}
}
            """,
            """
#define MAX_ITEMS 100
#include "library.lsl"

default
{
    state_entry()
    {
#ifdef DEBUG
        llOwnerSay("debug");
#endif
        llSay(0, "done");
    }
}
            """
        )
    }

    fun testStatesAndEvents() {
        doTest(
            """
default{
state_entry(){
state other;
}
}

state other{
state_entry(){
state default;
}
touch_start(integer total_number){
llSay(0,"Touched");
}
}
            """,
            """
default
{
    state_entry()
    {
        state other;
    }
}

state other
{
    state_entry()
    {
        state default;
    }

    touch_start(integer total_number)
    {
        llSay(0, "Touched");
    }
}
            """
        )
    }

    fun testComplexExpressionsAndComments() {
        doTest(
            """
default{
state_entry(){
// Line comment
/* Block comment */
vector v=<1.0,2.0,3.0>;
rotation r=<0.0,0.0,0.0,1.0>;
list l=[1,"two",<1.0,2.0,3.0>,[4,5]];
integer res=(1+2)*(3-4)/(5%2);
@start;
if(res<0){
jump start;
}
}
}
            """,
            """
default
{
    state_entry()
    {
        // Line comment
        /* Block comment */
        vector v = <1.0, 2.0, 3.0>;
        rotation r = <0.0, 0.0, 0.0, 1.0>;
        list l = [1, "two", <1.0, 2.0, 3.0>, [4, 5]];
        integer res = (1 + 2) * (3 - 4) / (5 % 2);
        @start;
        if (res < 0)
        {
            jump start;
        }
    }
}
            """
        )
    }

    fun testForLoopVariants() {
        doTest(
            """
default{
state_entry(){
integer i=0;
for(;i<10;i++){
}
for(;;){
jump end;
}
@end;
}
}
            """,
            """
default
{
    state_entry()
    {
        integer i = 0;
        for (; i < 10; i++)
        {
        }
        for (;;)
        {
            jump end;
        }
        @end;
    }
}
            """
        )
    }

    fun testConfigurableDisplayName() {
        val provider = LslLanguageCodeStyleSettingsProvider()
        assertEquals("LSL", provider.configurableDisplayName)
    }

    fun testInlineBracesWhenConfigured() {
        val commonSettings = com.intellij.psi.codeStyle.CodeStyleSettingsManager.getSettings(project)
            .getCommonSettings(io.github.koollsl.lsl.LslLanguage.INSTANCE)
        val oldClassBrace = commonSettings.CLASS_BRACE_STYLE
        val oldMethodBrace = commonSettings.METHOD_BRACE_STYLE
        val oldBlockBrace = commonSettings.BRACE_STYLE
        val oldElseOnNewLine = commonSettings.ELSE_ON_NEW_LINE

        try {
            commonSettings.CLASS_BRACE_STYLE = com.intellij.psi.codeStyle.CommonCodeStyleSettings.END_OF_LINE
            commonSettings.METHOD_BRACE_STYLE = com.intellij.psi.codeStyle.CommonCodeStyleSettings.END_OF_LINE
            commonSettings.BRACE_STYLE = com.intellij.psi.codeStyle.CommonCodeStyleSettings.END_OF_LINE
            commonSettings.ELSE_ON_NEW_LINE = false

            doTest(
                """
DisplayMaybe(string s){
llOwnerSay(s);
}

default{
state_entry(){
if(1){
DisplayMaybe("hello");
}else{
DisplayMaybe("bye");
}
}
}
                """,
                """
DisplayMaybe(string s) {
    llOwnerSay(s);
}

default {
    state_entry() {
        if (1) {
            DisplayMaybe("hello");
        } else {
            DisplayMaybe("bye");
        }
    }
}
                """
            )
        } finally {
            commonSettings.CLASS_BRACE_STYLE = oldClassBrace
            commonSettings.METHOD_BRACE_STYLE = oldMethodBrace
            commonSettings.BRACE_STYLE = oldBlockBrace
            commonSettings.ELSE_ON_NEW_LINE = oldElseOnNewLine
        }
    }
}
