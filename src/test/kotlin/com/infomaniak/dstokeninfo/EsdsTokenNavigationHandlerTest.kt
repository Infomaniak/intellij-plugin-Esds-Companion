package com.infomaniak.dstokeninfo

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Drives an actual click on a hint, down to the editor it ends up opening.
 *
 * [EsdsTokenNavigatorTest] only covers resolution; this is what catches a target being resolved
 * but never opened.
 */
class EsdsTokenNavigationHandlerTest : BasePlatformTestCase() {

    /** See [EsdsTokenCompletionContributorTest]: K2 needs a fresh project per test. */
    private val projectDescriptor = LightProjectDescriptor()

    override fun getProjectDescriptor(): LightProjectDescriptor = projectDescriptor

    private fun click(category: String, token: String) {
        val handler = EsdsTokenNavigationHandler()
        val promise = handler.navigateTo(myFixture.editor, EsdsTokenNavigationHandler.payloadFor(category, token))

        PlatformTestUtil.waitForPromise(promise!!)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    fun `test clicking a hint opens the file defining the token, at the right line`() {
        myFixture.addFileToProject("DefaultIconTokens.kt", DEFAULT_ICON_TOKENS)
        myFixture.configureByText("Screen.kt", "val size = EsdsTheme.icon.sizeSm")

        click("icon", "sizeSm")

        val opened = checkNotNull(FileEditorManager.getInstance(project).selectedTextEditor)
        assertEquals("DefaultIconTokens.kt", opened.virtualFile.name)

        val line = opened.document.let { it.getText(it.getLineRangeOf(opened.caretModel.offset)) }
        assertEquals("  sizeSm = IntermediateDefault.IconSizeSm,", line)
    }

    fun `test clicking a hint keeps the current editor when the design system has no sources`() {
        myFixture.configureByText("Screen.kt", "val size = EsdsTheme.icon.sizeSm")

        click("icon", "sizeSm")

        val opened = checkNotNull(FileEditorManager.getInstance(project).selectedTextEditor)
        assertEquals("Screen.kt", opened.virtualFile.name)
    }

    private companion object {
        val DEFAULT_ICON_TOKENS = """
            package com.infomaniak.designsystem.core.defaultvalues

            internal val DefaultIconTokens = IconTokens(
              sizeXs = IntermediateDefault.IconSizeXs,
              sizeSm = IntermediateDefault.IconSizeSm,
            )
        """.trimIndent()

        fun com.intellij.openapi.editor.Document.getLineRangeOf(offset: Int): com.intellij.openapi.util.TextRange {
            val line = getLineNumber(offset)
            return com.intellij.openapi.util.TextRange(getLineStartOffset(line), getLineEndOffset(line))
        }
    }
}
