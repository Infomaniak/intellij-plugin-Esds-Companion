package com.infomaniak.dstokeninfo

/**
 * Recognises references to design system tokens, such as `EsdsTheme.icon.sizeSm`.
 *
 * Doing this properly would mean resolving the `EsdsTheme.icon` receiver type through the
 * Kotlin analysis API, which ties the plugin to a specific Kotlin plugin version. A textual
 * match keeps the plugin dependency-free, and therefore compatible with every Android Studio
 * release in both K1 and K2 mode.
 *
 * The receiver must be a `*Theme` qualifier. That covers `EsdsTheme` and per-app wrappers
 * such as `MailTheme`, while avoiding a bogus `20dp` next to an unrelated API that merely
 * happens to expose an `icon`, `spacing` or `radius` member.
 */
internal object EsdsTokenMatcher {

    /** Only look at the tail of the file: completion runs on every keystroke. */
    private const val LOOKBEHIND = 256

    private const val THEME_SUFFIX = "Theme"

    /** `SomeTheme . category . partiallyTypedToken`, ending exactly at the caret. */
    private val PREFIX_REGEX = Regex("""(\w+)\s*\.\s*(\w+)\s*\.\s*\w*$""")

    /** A complete `SomeTheme.category.token` reference. */
    private val EXPRESSION_REGEX = Regex("""(\w+)\s*\.\s*(\w+)\s*\.\s*(\w+)""")

    /**
     * Returns the token category (`icon`, `spacing`, `radius`) being completed, or `null`
     * when the caret is not after such a receiver.
     */
    fun detectCategoryBeforeCaret(fileText: CharSequence, caretOffset: Int): String? {
        if (caretOffset <= 0 || caretOffset > fileText.length) return null

        val from = (caretOffset - LOOKBEHIND).coerceAtLeast(0)
        val prefix = fileText.subSequence(from, caretOffset)

        val match = PREFIX_REGEX.find(prefix) ?: return null
        if (match.range.last != prefix.length - 1) return null

        val (theme, category) = match.destructured
        if (!theme.endsWith(THEME_SUFFIX)) return null

        return category.takeIf { it in EsdsTokenValues.knownCategories }
    }

    /** A resolved token reference: which category and token it is, and its value. */
    data class Reference(val category: String, val token: String, val value: EsdsTokenValues.Token)

    /**
     * Resolves a whole token reference, e.g. `EsdsTheme.icon.sizeSm` -> `20dp`.
     * Returns `null` when [expression] is not a design system token reference.
     */
    fun resolveExpression(expression: CharSequence): Reference? {
        val match = EXPRESSION_REGEX.matchEntire(expression) ?: return null

        val (theme, category, token) = match.destructured
        if (!theme.endsWith(THEME_SUFFIX)) return null

        val value = EsdsTokenValues.tokenOf(category, token) ?: return null
        return Reference(category, token, value)
    }
}
