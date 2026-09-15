package com.infomaniak.dstokeninfo

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class EsdsReceiverDetectorTest {

    private fun detect(code: String): String? = detectAtCaret(code)

    /** `code` must contain a single `|` marking the caret position. */
    private fun detectAtCaret(code: String): String? {
        val offset = code.indexOf('|')
        require(offset >= 0) { "Missing caret marker" }
        return EsdsReceiverDetector.detectCategory(code.replace("|", ""), offset)
    }

    @Test
    fun `detects icon on a fully qualified theme access`() {
        assertEquals("icon", detect("Icon(modifier = Modifier.size(EsdsTheme.icon.|))"))
    }

    @Test
    fun `detects category from a partially typed token`() {
        assertEquals("icon", detect("EsdsTheme.icon.size|"))
        assertEquals("spacing", detect("EsdsTheme.spacing.m|"))
        assertEquals("radius", detect("EsdsTheme.radius.l|"))
    }

    @Test
    fun `tolerates whitespace around dots`() {
        assertEquals("spacing", detect("EsdsTheme\n    .spacing\n    .|"))
    }

    @Test
    fun `accepts per-app theme wrappers`() {
        assertEquals("spacing", detect("MyAppTheme.spacing.|"))
        assertEquals("icon", detect("MailTheme.icon.si|"))
    }

    @Test
    fun `ignores look-alike receivers from unrelated libraries`() {
        assertNull(detect("SomeOtherLib.spacing.|"))
        assertNull(detect("binding.icon.|"))
    }

    @Test
    fun `ignores an unqualified category`() {
        assertNull(detect("icon.|"))
    }

    @Test
    fun `ignores unknown categories`() {
        assertNull(detect("EsdsTheme.typography.|"))
    }

    @Test
    fun `ignores non token positions`() {
        assertNull(detect("val x = |"))
        assertNull(detect("EsdsTheme.icon.sizeSm + |"))
    }

    @Test
    fun `resolves the documented example value`() {
        assertEquals("20dp", EsdsTokenValues.valueOf("icon", "sizeSm"))
        assertEquals("8dp", EsdsTokenValues.valueOf("spacing", "md"))
        assertEquals("full", EsdsTokenValues.valueOf("radius", "full"))
        assertNull(EsdsTokenValues.valueOf("icon", "md"))
    }
}
