package com.infomaniak.esdscompanion

import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File

/** Runs against real compiled icons from the design system, copied into `testData/esds-icons`. */
class EsdsIconTest : BasePlatformTestCase() {

    private val projectDescriptor = LightProjectDescriptor()

    override fun getProjectDescriptor(): LightProjectDescriptor = projectDescriptor

    override fun setUp() {
        super.setUp()
        ModuleRootModificationUtil.addModuleLibrary(module, VfsUtil.pathToUrl(File(ICONS_ROOT).absolutePath))
        // Kotlin cannot read the compiled declarations of a design system built by a newer compiler than
        // the test IDE, so completion runs against sources; previews still come from the class files.
        myFixture.addFileToProject(
            "EsdsIcons.kt",
            """
            package com.infomaniak.designsystem.icons
            object EsdsIcons
            sealed interface Symbol {
                val ExtraSmall: Any
                val Small: Any
                val Medium: Any
                val Large: Any
                val ExtraLarge: Any
                val current: Any
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "Plus.kt",
            "package com.infomaniak.designsystem.icons.symbols\n" +
                "import com.infomaniak.designsystem.icons.*\nval EsdsIcons.Plus: Symbol get() = TODO()",
        )
    }

    private fun classBytes(size: String, name: String) =
        File("$ICONS_ROOT/com/infomaniak/designsystem/icons/imagevector/$size/${name}Kt.class").readBytes()

    fun `test the icon size is read from the bytecode`() {
        assertEquals("24dp", EsdsIconVector.read(classBytes("medium", "Plus"), "Plus")?.sizeLabel)
        assertEquals("16dp", EsdsIconVector.read(classBytes("extrasmall", "Plus"), "Plus")?.sizeLabel)
        assertEquals("40dp", EsdsIconVector.read(classBytes("extralarge", "Plus"), "Plus")?.sizeLabel)
        // Arcs, several paths, an even-odd fill.
        assertNotNull(EsdsIconVector.read(classBytes("medium", "ProductKmeetFilled"), "ProductKmeetFilled"))
        assertNotNull(EsdsIconVector.read(classBytes("medium", "Bell"), "Bell"))
        assertNull(EsdsIconVector.read(byteArrayOf(1, 2, 3), "Plus"))
    }

    fun `test the preview paints the icon in the requested color`() {
        val vector = EsdsIconVector.read(classBytes("medium", "Plus"), "Plus")!!
        val image = BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB)
        vector.Preview(24) { Color.RED }.paintIcon(null, image.createGraphics(), 0, 0)

        assertEquals(Color.RED.rgb, image.getRGB(12, 12) or 0xFF000000.toInt()) // Where both strokes of the plus cross.
        assertEquals(0, image.getRGB(1, 1))
    }

    private fun completeAndRender(code: String): Map<String, LookupElementPresentation> {
        myFixture.configureByText("Screen.kt", "$IMPORT\n$code")
        myFixture.completeBasic()
        return myFixture.lookupElements.orEmpty().associate {
            it.lookupString to LookupElementPresentation().also(it::renderElement)
        }
    }

    private val LookupElementPresentation.previewSize get() = (icon as? EsdsIconVector.Preview)?.vector?.sizeLabel

    fun `test completing an icon previews it at the default size`() {
        val rendered = completeAndRender("fun screen() { EsdsIcons.<caret> }")

        assertEquals("24dp", rendered.getValue("Plus").previewSize)
    }

    fun `test completing a size shows its preview and dp size`() {
        val rendered = completeAndRender("fun screen() { EsdsIcons.Plus.<caret> }")

        assertEquals("24dp", rendered.getValue("Medium").typeText)
        assertEquals("24dp", rendered.getValue("Medium").previewSize)
        assertEquals("16dp", rendered.getValue("ExtraSmall").typeText)
        assertEquals("16dp", rendered.getValue("ExtraSmall").previewSize)
        assertNull(rendered.getValue("current").previewSize)
    }

    fun `test icons used in code get a gutter preview`() {
        myFixture.configureByText(
            "Screen.kt",
            "$IMPORT\nval a = EsdsIcons.Plus.ExtraSmall\nval b = EsdsIcons.Plus\nval c = EsdsIcons.Plus.current",
        )

        val tooltips = myFixture.findAllGutters().filter { it.icon is EsdsIconVector.Preview }.map { it.tooltipText }
        assertEquals(
            listOf("EsdsIcons.Plus.ExtraSmall · 16dp", "EsdsIcons.Plus.Medium · 24dp", "EsdsIcons.Plus.Medium · 24dp"),
            tooltips,
        )
    }

    private companion object {
        const val ICONS_ROOT = "src/test/testData/esds-icons"
        const val IMPORT = "import com.infomaniak.designsystem.icons.EsdsIcons\nimport com.infomaniak.designsystem.icons.symbols.Plus\n"
    }
}
