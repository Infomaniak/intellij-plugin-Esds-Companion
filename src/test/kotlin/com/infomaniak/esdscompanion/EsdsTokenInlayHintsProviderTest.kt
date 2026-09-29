package com.infomaniak.esdscompanion

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase

/**
 * Runs the real inlay hints pass over Kotlin code. Expected hints are written inline with the
 * platform's `/*<# ... #>*/` markers, where `[payload:handlerId]` is the click action attached to
 * the hint, so these tests also pin down what clicking a hint will navigate to.
 */
class EsdsTokenInlayHintsProviderTest : DeclarativeInlayHintsProviderTestCase() {

    /** See [EsdsTokenCompletionContributorTest]: K2 needs a fresh project per test. */
    private val projectDescriptor = LightProjectDescriptor()

    override fun getProjectDescriptor(): LightProjectDescriptor = projectDescriptor

    private fun doTest(code: String) {
        doTestProvider("Screen.kt", code, EsdsTokenInlayHintsProvider())
    }

    fun `test token references show their value inline`() {
        doTest(
            """
            fun screen() {
                val iconSize = EsdsTheme.icon.sizeSm/*<# [icon/sizeSm:com.infomaniak.esdscompanion.navigateToToken]20dp #>*/
                val padding = EsdsTheme.spacing.md/*<# [spacing/md:com.infomaniak.esdscompanion.navigateToToken]8dp #>*/
                val shape = EsdsTheme.radius.full/*<# [radius/full:com.infomaniak.esdscompanion.navigateToToken]full #>*/
            }
            """.trimIndent(),
        )
    }

    fun `test the hint sits at the call site inside a call`() {
        doTest(
            """
            fun screen() {
                Box(Modifier.padding(EsdsTheme.spacing.lg/*<# [spacing/lg:com.infomaniak.esdscompanion.navigateToToken]12dp #>*/))
            }
            """.trimIndent(),
        )
    }

    fun `test per-app theme wrappers are supported`() {
        doTest(
            """
            fun screen() {
                val iconSize = MailTheme.icon.sizeXl/*<# [icon/sizeXl:com.infomaniak.esdscompanion.navigateToToken]40dp #>*/
            }
            """.trimIndent(),
        )
    }

    fun `test nothing is shown for unrelated receivers or unknown tokens`() {
        doTest(
            """
            fun screen() {
                val a = binding.icon.sizeSm
                val b = EsdsTheme.icon.sizeHuge
                val c = EsdsTheme.typography.body
                val d = EsdsTheme.icon
            }
            """.trimIndent(),
        )
    }
}
