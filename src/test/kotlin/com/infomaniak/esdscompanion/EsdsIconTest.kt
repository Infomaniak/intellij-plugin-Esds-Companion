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
            "package com.infomaniak.designsystem.icons\nobject EsdsIcons { object ExtraSmall; object Medium }",
        )
        myFixture.addFileToProject(
            "Plus.kt",
            "package com.infomaniak.designsystem.icons.imagevector.medium\n" +
                "import com.infomaniak.designsystem.icons.EsdsIcons\nval EsdsIcons.Medium.Plus: Any get() = TODO()",
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

    fun `test completing an icon shows its preview`() {
        myFixture.configureByText("Screen.kt", "$IMPORT\nfun screen() { EsdsIcons.Medium.<caret> }")
        myFixture.completeBasic()

        val plus = myFixture.lookupElements.orEmpty().first { it.lookupString == "Plus" }
        val icon = LookupElementPresentation().also(plus::renderElement).icon
        assertEquals("24dp", (icon as EsdsIconVector.Preview).vector.sizeLabel)
    }

    fun `test completing a size category shows its icon size`() {
        myFixture.configureByText("Screen.kt", "$IMPORT\nfun screen() { EsdsIcons.<caret> }")
        myFixture.completeBasic()

        val rendered = myFixture.lookupElements.orEmpty().associate {
            it.lookupString to LookupElementPresentation().also(it::renderElement).typeText
        }
        assertEquals("24dp", rendered["Medium"])
        assertEquals("16dp", rendered["ExtraSmall"])
    }

    fun `test icons used in code get a gutter preview`() {
        myFixture.configureByText("Screen.kt", "$IMPORT\nval icon = EsdsIcons.Medium.Plus\nval other = EsdsIcons.Medium")

        val tooltips = myFixture.findAllGutters().filter { it.icon is EsdsIconVector.Preview }.map { it.tooltipText }
        assertEquals(listOf("EsdsIcons.Medium.Plus · 24dp"), tooltips)
    }

    private companion object {
        const val ICONS_ROOT = "src/test/testData/esds-icons"
        const val IMPORT = "import com.infomaniak.designsystem.icons.EsdsIcons\n"
    }
}
