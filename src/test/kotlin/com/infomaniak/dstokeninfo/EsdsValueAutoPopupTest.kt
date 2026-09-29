package com.infomaniak.dstokeninfo

import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.util.ThrowableRunnable

/**
 * Types like a user would, with the completion popup opening by itself. This covers the case where
 * the popup was not already open when the digits are typed, which explicit completion does not.
 */
class EsdsValueAutoPopupTest : BasePlatformTestCase() {

    /** See [EsdsTokenCompletionContributorTest]: K2 needs a fresh project per test. */
    private val projectDescriptor = LightProjectDescriptor()

    private lateinit var tester: CompletionAutoPopupTester

    override fun getProjectDescriptor(): LightProjectDescriptor = projectDescriptor

    /** The autopopup is scheduled asynchronously, so the test must not hold the EDT. */
    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        tester = CompletionAutoPopupTester(myFixture)
    }

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        tester.runWithAutoPopupEnabled(testRunnable)
    }

    fun `test typing digits after a token category opens the popup filtered by value`() {
        EdtTestUtil.runInEdtAndWait<Throwable> {
            myFixture.configureByText("Screen.kt", "fun screen() { EsdsTheme.spacing.<caret> }")
        }

        tester.typeWithPauses("1")

        val items = EdtTestUtil.runInEdtAndGet<List<String>, Throwable> { myFixture.lookupElementStrings.orEmpty() }
        assertEquals(listOf("lg", "xl", "eightXl"), items)
    }
}
