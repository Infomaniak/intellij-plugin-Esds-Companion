package com.infomaniak.esdscompanion

/**
 * Recognises references to design system icons, such as `EsdsIcons.Medium.Plus`.
 *
 * Like [EsdsTokenMatcher], this works on text rather than resolved Kotlin symbols, which keeps the
 * plugin independent of the Kotlin plugin's version. Every icon is an extension property on one of
 * the size objects nested in `EsdsIcons`, so the `EsdsIcons.<Size>.<Icon>` shape is distinctive.
 *
 * This is the single place deciding what an icon reference looks like, shared by completion, the
 * gutter previews and anything else that needs to know which icon a piece of code uses.
 */
internal object EsdsIconMatcher {

    const val ICONS_OBJECT = "EsdsIcons"

    /** Only look at the tail of the file: completion runs on every keystroke. */
    private const val LOOKBEHIND = 256

    /** `EsdsIcons . partiallyTypedSize`, ending exactly at the caret. */
    private val SIZE_PREFIX_REGEX = Regex("""(?<!\w)$ICONS_OBJECT\s*\.\s*\w*$""")

    /** `EsdsIcons . Size . partiallyTypedIcon`, ending exactly at the caret. */
    private val ICON_PREFIX_REGEX = Regex("""(?<!\w)$ICONS_OBJECT\s*\.\s*(\w+)\s*\.\s*\w*$""")

    /** A whole `EsdsIcons.Size.Icon` reference, optionally fully qualified, without whitespace. */
    private val EXPRESSION_REGEX = Regex("""(?:[\w.]*\.)?$ICONS_OBJECT\.(\w+)\.(\w+)""")

    private val WHITESPACE = Regex("""\s+""")

    /** An icon reference: `size` is the nested object (`Medium`), `name` the icon (`Plus`). */
    data class IconReference(val size: String, val name: String)

    /** Whether the caret is completing a size right after `EsdsIcons.`. */
    fun isCompletingSize(fileText: CharSequence, caretOffset: Int): Boolean =
        tailBeforeCaret(fileText, caretOffset)?.let(SIZE_PREFIX_REGEX::containsMatchIn) == true

    /** The size (`Medium`) whose icons are being completed after `EsdsIcons.Medium.`, if any. */
    fun detectSizeOfIconBeforeCaret(fileText: CharSequence, caretOffset: Int): String? {
        val tail = tailBeforeCaret(fileText, caretOffset) ?: return null
        return ICON_PREFIX_REGEX.find(tail)?.groupValues?.get(1)
    }

    /**
     * Resolves a whole icon reference such as `EsdsIcons.Medium.Plus`, which may be fully qualified.
     * Returns `null` when [expression] is not an icon reference.
     */
    fun resolveExpression(expression: CharSequence): IconReference? {
        val match = EXPRESSION_REGEX.matchEntire(expression.replace(WHITESPACE, "")) ?: return null
        val (size, name) = match.destructured
        return IconReference(size, name)
    }

    private fun tailBeforeCaret(fileText: CharSequence, caretOffset: Int): CharSequence? {
        if (caretOffset <= 0 || caretOffset > fileText.length) return null
        return fileText.subSequence((caretOffset - LOOKBEHIND).coerceAtLeast(0), caretOffset)
    }
}
