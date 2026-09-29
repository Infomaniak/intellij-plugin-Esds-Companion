package com.infomaniak.dstokeninfo

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder

/**
 * Completes a token from the start of its value: `EsdsTheme.spacing.1` offers every spacing whose
 * value starts with 1 (`12dp`, `16dp`), and `EsdsTheme.spacing.16` narrows it down to `xl`.
 * Accepting an item replaces the typed digits with the token name.
 */
internal object EsdsValueCompletion {

    /** Pattern of a completion prefix that should switch over to looking tokens up by value. */
    const val DIGITS_PATTERN: String = "\\d+"

    fun addTokensMatchingValue(query: EsdsTokenMatcher.ValueQuery, result: CompletionResultSet) {
        val tokens = EsdsTokenValues.categories[query.category] ?: return
        val valueResult = result.withPrefixMatcher(ValuePrefixMatcher(query.digits))

        for ((name, token) in tokens) {
            val element = LookupElementBuilder.create(token, name)
                .withTypeText(token.value)
            // Smallest value first, so an exact `16` comes before a `160`.
            valueResult.addElement(PrioritizedLookupElement.withPriority(element, -numericValue(token)))
        }

        // Kotlin sees a number literal here: nothing it could add would make sense.
        result.stopHere()
    }

    private fun numericValue(token: EsdsTokenValues.Token): Double =
        token.value.takeWhile { it.isDigit() || it == '.' }.toDoubleOrNull() ?: Double.MAX_VALUE

    /** Matches items on the start of their token value instead of their name. */
    private class ValuePrefixMatcher(prefix: String) : PrefixMatcher(prefix) {

        override fun prefixMatches(element: LookupElement): Boolean {
            val token = element.`object` as? EsdsTokenValues.Token ?: return false
            return prefixMatches(token.value)
        }

        override fun isStartMatch(element: LookupElement): Boolean = prefixMatches(element)

        override fun prefixMatches(name: String): Boolean = name.startsWith(prefix)

        override fun cloneWithPrefix(prefix: String): PrefixMatcher = ValuePrefixMatcher(prefix)
    }
}
