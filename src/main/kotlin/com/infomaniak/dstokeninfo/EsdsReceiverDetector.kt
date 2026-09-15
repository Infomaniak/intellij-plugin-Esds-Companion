package com.infomaniak.dstokeninfo

/**
 * Figures out which design system token group is being completed, purely from the text
 * that precedes the caret.
 *
 * Resolving this properly would mean resolving the `EsdsTheme.icon` receiver type through
 * the Kotlin analysis API, which ties the plugin to a specific Kotlin plugin version. A
 * textual match keeps the plugin dependency-free (and therefore trivially compatible with
 * every Android Studio release), and covers the way the tokens are actually written:
 *
 *     EsdsTheme.icon.sizeSm
 *     EsdsTheme.spacing.md
 *     MyAppTheme.radius.lg
 */
internal object EsdsReceiverDetector {

    /** Only look at the tail of the file: completion runs on every keystroke. */
    private const val LOOKBEHIND = 256

    private const val THEME_SUFFIX = "Theme"

    /** `SomeTheme . category . partiallyTypedToken`, ending exactly at the caret. */
    private val RECEIVER_REGEX = Regex("""(\w+)\s*\.\s*(\w+)\s*\.\s*\w*$""")

    /**
     * Returns the token category (`icon`, `spacing`, `radius`) being completed, or `null`
     * when the caret is not after such a receiver.
     */
    fun detectCategory(fileText: CharSequence, caretOffset: Int): String? {
        if (caretOffset <= 0 || caretOffset > fileText.length) return null

        val from = (caretOffset - LOOKBEHIND).coerceAtLeast(0)
        val prefix = fileText.subSequence(from, caretOffset)

        val match = RECEIVER_REGEX.find(prefix) ?: return null
        if (match.range.last != prefix.length - 1) return null

        val (theme, category) = match.destructured

        // Requiring a `*Theme` qualifier keeps `EsdsTheme.icon` working and tolerates per-app
        // theme wrappers, while avoiding a bogus `20dp` next to an unrelated API that merely
        // happens to expose an `icon`, `spacing` or `radius` member.
        if (!theme.endsWith(THEME_SUFFIX)) return null

        return category.takeIf { it in EsdsTokenValues.knownCategories }
    }
}
