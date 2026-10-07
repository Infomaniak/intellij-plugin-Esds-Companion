package com.infomaniak.esdscompanion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Drives real Kotlin code completion to check that this contributor actually runs *before* the
 * Kotlin one. Registering it for `language="any"` silently broke exactly that, because the
 * platform appends `any` contributors after the language-specific ones.
 */
class EsdsTokenCompletionContributorTest : BasePlatformTestCase() {

    /**
     * JUnit builds a fresh test instance per method, so each test gets its own descriptor and
     * therefore its own light project. Without this, K2 analysis reuses the project and trips
     * over decompiled Kotlin builtins cached by the previous test.
     */
    private val projectDescriptor = LightProjectDescriptor()

    override fun getProjectDescriptor(): LightProjectDescriptor = projectDescriptor

    private fun completeAndRender(code: String): Map<String, String?> {
        myFixture.configureByText("Screen.kt", DESIGN_SYSTEM_STUB + code)
        myFixture.completeBasic()

        val elements: Array<LookupElement> = myFixture.lookupElements ?: emptyArray()
        return elements.associate { element ->
            val presentation = LookupElementPresentation().also(element::renderElement)
            element.lookupString to presentation.typeText
        }
    }

    fun `test icon tokens show their value instead of the Dp type`() {
        val rendered = completeAndRender("fun screen() { EsdsTheme.icon.si<caret> }")

        assertEquals("20dp", rendered["sizeSm"])
        assertEquals("24dp", rendered["sizeMd"])
    }

    fun `test spacing tokens are decorated too`() {
        val rendered = completeAndRender("fun screen() { EsdsTheme.spacing.<caret> }")

        assertEquals("8dp", rendered["md"])
        assertEquals("100dp", rendered["eightXl"])
    }

    fun `test opacity tokens show a percentage instead of the Float type`() {
        val rendered = completeAndRender("fun screen() { EsdsTheme.opacity.<caret> }")

        assertEquals("5%", rendered["ghost"])
        assertEquals("100%", rendered["full"])
    }

    fun `test unrelated members keep their original type text`() {
        val rendered = completeAndRender("fun screen() { unrelatedHolder.spacing.<caret> }")

        assertEquals("Dp", rendered["md"])
    }

    private fun lookupStringsFor(code: String): List<String> {
        myFixture.configureByText("Screen.kt", DESIGN_SYSTEM_STUB + code)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty()
    }

    fun `test digits list the tokens whose value starts with them, smallest first`() {
        assertEquals(listOf("lg", "xl", "eightXl"), lookupStringsFor("fun screen() { EsdsTheme.spacing.1<caret> }"))
        assertEquals(listOf("sizeSm", "sizeMd"), lookupStringsFor("fun screen() { EsdsTheme.icon.2<caret> }"))
        assertEquals(listOf("ghost", "medium"), lookupStringsFor("fun screen() { EsdsTheme.opacity.5<caret> }"))
    }

    fun `test a value matching a single token is replaced by its name`() {
        myFixture.configureByText("Screen.kt", DESIGN_SYSTEM_STUB + "fun screen() { EsdsTheme.spacing.16<caret> }")
        myFixture.completeBasic()

        assertTrue(myFixture.editor.document.text.endsWith("fun screen() { EsdsTheme.spacing.xl }"))
    }

    /** The popup opened on the dot only holds names: typing digits must switch it to values. */
    fun `test typing digits into an open popup narrows it down by value`() {
        myFixture.configureByText("Screen.kt", DESIGN_SYSTEM_STUB + "fun screen() { EsdsTheme.spacing.<caret> }")
        myFixture.completeBasic()

        myFixture.type('1')
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf("lg", "xl", "eightXl"), myFixture.lookupElementStrings)

        myFixture.type('6')
        assertEquals(listOf("xl"), myFixture.lookupElementStrings)

        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertTrue(myFixture.editor.document.text.endsWith("fun screen() { EsdsTheme.spacing.xl }"))
    }

    fun `test value lookup ignores unrelated receivers`() {
        assertEquals(emptyList<String>(), lookupStringsFor("fun screen() { unrelatedHolder.spacing.1<caret> }"))
    }

    private companion object {
        /**
         * Minimal stand-in for the design system: the real classes are not on the test
         * classpath, but completion only needs the shape of the API.
         */
        val DESIGN_SYSTEM_STUB = """
            package com.infomaniak.designsystem.core.theme

            class Dp
            class IconTokens(val sizeXs: Dp, val sizeSm: Dp, val sizeMd: Dp, val sizeLg: Dp, val sizeXl: Dp)
            class SpacingTokens(val md: Dp, val lg: Dp, val xl: Dp, val eightXl: Dp)
            class OpacityTokens(val none: Float, val ghost: Float, val medium: Float, val full: Float)

            object EsdsTheme {
                val icon: IconTokens get() = throw UnsupportedOperationException()
                val spacing: SpacingTokens get() = throw UnsupportedOperationException()
                val opacity: OpacityTokens get() = throw UnsupportedOperationException()
            }

            object UnrelatedHolder {
                val spacing: SpacingTokens get() = throw UnsupportedOperationException()
            }

            val unrelatedHolder = UnrelatedHolder

        """.trimIndent()
    }
}
