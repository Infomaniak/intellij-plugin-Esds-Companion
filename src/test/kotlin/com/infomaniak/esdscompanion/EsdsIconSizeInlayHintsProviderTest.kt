package com.infomaniak.esdscompanion

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import java.io.File

class EsdsIconSizeInlayHintsProviderTest : DeclarativeInlayHintsProviderTestCase() {

    /** See [EsdsTokenCompletionContributorTest]: K2 needs a fresh project per test. */
    private val projectDescriptor = LightProjectDescriptor()

    override fun getProjectDescriptor(): LightProjectDescriptor = projectDescriptor

    override fun setUp() {
        super.setUp()
        // Sizes are read from the compiled icons, as in the IDE.
        ModuleRootModificationUtil.addModuleLibrary(module, VfsUtil.pathToUrl(File("src/test/testData/esds-icons").absolutePath))
    }

    fun `test sizes of any Symbol show their dp size`() {
        doTestProvider(
            "Screen.kt",
            """
            sealed interface Symbol {
                val ExtraSmall: Any
                val Small: Any
                val Large: Any
            }
            object EsdsIcons
            val EsdsIcons.Plus: Symbol get() = TODO()
            enum class TextSize { Small }
            fun pick(): Symbol = TODO()

            fun screen(icon: Symbol) {
                val a = EsdsIcons.Plus.Small/*<# 20dp #>*/
                val b = icon.Large/*<# 32dp #>*/
                val c = pick().ExtraSmall/*<# 16dp #>*/
                val d = TextSize.Small
                val e = EsdsIcons.Plus
            }
            """.trimIndent(),
            EsdsIconSizeInlayHintsProvider(),
        )
    }
}
