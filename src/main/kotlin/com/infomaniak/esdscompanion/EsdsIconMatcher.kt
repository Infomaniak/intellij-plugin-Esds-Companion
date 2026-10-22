package com.infomaniak.esdscompanion

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement

/**
 * Recognises references to design system icons, such as `EsdsIcons.Plus.Medium`.
 *
 * Like [EsdsTokenMatcher], this works on text rather than resolved Kotlin symbols, which keeps the
 * plugin independent of the Kotlin plugin's version. Every icon is a `Symbol` extension property on
 * `EsdsIcons`, exposing one `ImageVector` per size, so the `EsdsIcons.<Icon>.<Size>` shape is distinctive.
 *
 * This is the single place deciding what an icon reference looks like, shared by completion, the
 * gutter previews and anything else that needs to know which icon a piece of code uses.
 */
internal object EsdsIconMatcher {

    const val ICONS_OBJECT = "EsdsIcons"

    /** The sizes every `Symbol` exposes, matching the nested `EsdsIcons.<Size>` objects. */
    val SIZES = setOf("ExtraSmall", "Small", "Medium", "Large", "ExtraLarge")

    /** What `Symbol.current` and a bare `EsdsIcons.Plus` resolve to by default (`LocalEsdsIconSize`). */
    const val DEFAULT_SIZE = "Medium"

    /** Only look at the tail of the file: completion runs on every keystroke. */
    private const val LOOKBEHIND = 256

    /** `EsdsIcons . partiallyTypedIcon`, ending exactly at the caret. */
    private val ICON_PREFIX_REGEX = Regex("""(?<!\w)$ICONS_OBJECT\s*\.\s*\w*$""")

    /** `EsdsIcons . Icon . partiallyTypedSize`, ending exactly at the caret. */
    private val SIZE_PREFIX_REGEX = Regex("""(?<!\w)$ICONS_OBJECT\s*\.\s*(\w+)\s*\.\s*\w*$""")

    /** A whole `EsdsIcons.Icon[.Size]` reference, optionally fully qualified, without whitespace. */
    private val EXPRESSION_REGEX = Regex("""(?:[\w.]*\.)?$ICONS_OBJECT\.(\w+)(?:\.(\w+))?""")

    private val WHITESPACE = Regex("""\s+""")

    /** `receiver . partiallyTypedMember`, ending exactly at the caret. */
    private val MEMBER_PREFIX_REGEX = Regex("""\.\s*\w*$""")

    /** An icon reference: `name` is the icon (`Plus`), `size` the size it is drawn at (`Medium`). */
    data class IconReference(val name: String, val size: String)

    /** Whether the caret is completing an icon name right after `EsdsIcons.`. */
    fun isCompletingIcon(fileText: CharSequence, caretOffset: Int): Boolean =
        tailBeforeCaret(fileText, caretOffset)?.let(ICON_PREFIX_REGEX::containsMatchIn) == true

    /** The icon (`Plus`) whose sizes are being completed after `EsdsIcons.Plus.`, if any. */
    fun detectIconBeforeCaret(fileText: CharSequence, caretOffset: Int): String? {
        val tail = tailBeforeCaret(fileText, caretOffset) ?: return null
        return SIZE_PREFIX_REGEX.find(tail)?.groupValues?.get(1)
    }

    /**
     * Resolves a whole icon reference such as `EsdsIcons.Plus.Small`, which may be fully qualified.
     * `EsdsIcons.Plus` and `EsdsIcons.Plus.current` resolve to [DEFAULT_SIZE].
     * Returns `null` when [expression] is not an icon reference.
     */
    fun resolveExpression(expression: CharSequence): IconReference? {
        val match = EXPRESSION_REGEX.matchEntire(expression.replace(WHITESPACE, "")) ?: return null
        val (name, size) = match.destructured
        return when (size) {
            "", "current" -> IconReference(name, DEFAULT_SIZE)
            in SIZES -> IconReference(name, size)
            else -> null
        }
    }

    /** Whether the caret is completing a member, as in `symbol.`, whatever the receiver. */
    fun isCompletingMember(fileText: CharSequence, caretOffset: Int): Boolean =
        tailBeforeCaret(fileText, caretOffset)?.let(MEMBER_PREFIX_REGEX::containsMatchIn) == true

    /** Whether [declaration] is one of the sizes declared by the `Symbol` interface, such as `Symbol.Small`. */
    fun isSymbolSize(declaration: PsiElement?): Boolean =
        (declaration as? PsiNamedElement)?.name in SIZES &&
            (declaration?.parent?.parent as? PsiNamedElement)?.name == SYMBOL_INTERFACE

    private const val SYMBOL_INTERFACE = "Symbol"

    private fun tailBeforeCaret(fileText: CharSequence, caretOffset: Int): CharSequence? {
        if (caretOffset <= 0 || caretOffset > fileText.length) return null
        return fileText.subSequence((caretOffset - LOOKBEHIND).coerceAtLeast(0), caretOffset)
    }
}
