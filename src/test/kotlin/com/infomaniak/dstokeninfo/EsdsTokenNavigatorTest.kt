package com.infomaniak.dstokeninfo

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Checks that clicking an inlay hint lands on the line actually defining the value, using copies
 * of the real design system files.
 */
class EsdsTokenNavigatorTest : BasePlatformTestCase() {

    /** See [EsdsTokenCompletionContributorTest]: K2 needs a fresh project per test. */
    private val projectDescriptor = LightProjectDescriptor()

    override fun getProjectDescriptor(): LightProjectDescriptor = projectDescriptor

    private fun findDefinition(category: String, tokenName: String): EsdsTokenNavigator.Target? {
        val token = checkNotNull(EsdsTokenValues.tokenOf(category, tokenName)) { "Unknown $category.$tokenName" }
        return EsdsTokenNavigator.findDefinition(project, category, tokenName, token)
    }

    /** Returns the line of the resolved target, which is what the user ends up looking at. */
    private fun targetLine(target: EsdsTokenNavigator.Target, sources: String): String {
        return sources.substring(0, target.offset).substringAfterLast('\n') +
            sources.substring(target.offset).substringBefore('\n')
    }

    fun `test navigates to the default theme entry naming the token`() {
        myFixture.addFileToProject("DefaultIconTokens.kt", DEFAULT_ICON_TOKENS)
        myFixture.addFileToProject("IntermediateDefault.kt", INTERMEDIATE_DEFAULT)

        val target = checkNotNull(findDefinition("icon", "sizeSm"))

        assertEquals("DefaultIconTokens.kt", target.fileName)
        assertEquals("  sizeSm = IntermediateDefault.IconSizeSm,", targetLine(target, DEFAULT_ICON_TOKENS))
    }

    fun `test falls back to the intermediate mapping when the theme file is absent`() {
        myFixture.addFileToProject("IntermediateDefault.kt", INTERMEDIATE_DEFAULT)

        val target = checkNotNull(findDefinition("icon", "sizeSm"))

        assertEquals("IntermediateDefault.kt", target.fileName)
        assertEquals("  val IconSizeSm: Dp = Scale20", targetLine(target, INTERMEDIATE_DEFAULT))
    }

    fun `test falls back to the primitive holding the literal value`() {
        myFixture.addFileToProject("ScalePrimitiveTokens.kt", SCALE_PRIMITIVES)

        val target = checkNotNull(findDefinition("icon", "sizeSm"))

        assertEquals("ScalePrimitiveTokens.kt", target.fileName)
        assertEquals("val Scale20: Dp = 20.dp", targetLine(target, SCALE_PRIMITIVES))
    }

    fun `test does not confuse a token with a qualified reference of the same name`() {
        myFixture.addFileToProject("DefaultSpacingTokens.kt", DEFAULT_SPACING_TOKENS)

        val target = checkNotNull(findDefinition("spacing", "md"))

        // `IntermediateDefault.SpacingMd` on the right-hand side must not win over `md =`.
        assertEquals("  md = IntermediateDefault.SpacingMd,", targetLine(target, DEFAULT_SPACING_TOKENS))
    }

    /**
     * The virtual file must be carried over as-is: rebuilding a `file://` URL from its path breaks
     * as soon as the design system comes from a library, where sources live inside a jar.
     */
    fun `test reports the file it found rather than a path`() {
        val file = myFixture.addFileToProject("DefaultIconTokens.kt", DEFAULT_ICON_TOKENS).virtualFile

        val target = checkNotNull(findDefinition("icon", "sizeSm"))

        assertEquals(file, target.file)
    }

    fun `test resolves nothing when the design system is not in the project`() {
        assertNull(findDefinition("icon", "sizeSm"))
    }

    private companion object {
        /** Verbatim copies of the real generated sources. */
        val DEFAULT_ICON_TOKENS = """
            package com.infomaniak.designsystem.core.defaultvalues

            internal val DefaultIconTokens = IconTokens(
              sizeXs = IntermediateDefault.IconSizeXs,
              sizeMd = IntermediateDefault.IconSizeMd,
              sizeLg = IntermediateDefault.IconSizeLg,
              sizeSm = IntermediateDefault.IconSizeSm,
              sizeXl = IntermediateDefault.IconSizeXl,
            )
        """.trimIndent()

        val DEFAULT_SPACING_TOKENS = """
            package com.infomaniak.designsystem.core.defaultvalues

            internal val DefaultSpacingTokens = SpacingTokens(
              none = IntermediateDefault.SpacingNone,
              md = IntermediateDefault.SpacingMd,
            )
        """.trimIndent()

        val INTERMEDIATE_DEFAULT = """
            package com.infomaniak.designsystem.core.defaultvalues.internal

            internal object IntermediateDefault {
              val IconSizeXs: Dp = Scale16
              val IconSizeSm: Dp = Scale20
            }
        """.trimIndent()

        val SCALE_PRIMITIVES = """
            package com.infomaniak.designsystem.primitivetokens

            val Scale16: Dp = 16.dp
            val Scale20: Dp = 20.dp
        """.trimIndent()
    }
}
