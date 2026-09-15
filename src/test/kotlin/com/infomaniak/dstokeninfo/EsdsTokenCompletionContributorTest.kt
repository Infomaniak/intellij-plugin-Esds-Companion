package com.infomaniak.dstokeninfo

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
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

    fun `test unrelated members keep their original type text`() {
        val rendered = completeAndRender("fun screen() { unrelatedHolder.spacing.<caret> }")

        assertEquals("Dp", rendered["md"])
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
            class SpacingTokens(val md: Dp, val eightXl: Dp)

            object EsdsTheme {
                val icon: IconTokens get() = throw UnsupportedOperationException()
                val spacing: SpacingTokens get() = throw UnsupportedOperationException()
            }

            object UnrelatedHolder {
                val spacing: SpacingTokens get() = throw UnsupportedOperationException()
            }

            val unrelatedHolder = UnrelatedHolder

        """.trimIndent()
    }
}
