package com.infomaniak.esdscompanion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.patterns.StandardPatterns

/**
 * Replaces the type text shown on the right of a completion item with the concrete value
 * of the design system token, so `EsdsTheme.icon.sizeSm` reads `sizeSm    20dp` instead of
 * the useless `sizeSm    Dp`.
 *
 * Registered with `order="first"` so it can wrap the items produced by the Kotlin plugin.
 *
 * Typing digits instead of a name looks the token up by value: see [EsdsValueCompletion].
 */
class EsdsTokenCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        if (!file.name.endsWith(".kt") && !file.name.endsWith(".kts")) return

        val text = file.viewProvider.contents

        val valueQuery = EsdsTokenMatcher.detectValueQueryBeforeCaret(text, parameters.offset)
        if (valueQuery != null) {
            EsdsValueCompletion.addTokensMatchingValue(valueQuery, result)
            return
        }

        val category = EsdsTokenMatcher.detectCategoryBeforeCaret(text, parameters.offset)
            ?: return decorateIcons(parameters, result, text)

        // The popup opened on `EsdsTheme.spacing.` only holds names, which a digit can never match.
        // Restarting on digits lets the branch above take over instead of the popup closing.
        result.restartCompletionOnPrefixChange(StandardPatterns.string().matches(EsdsValueCompletion.DIGITS_PATTERN))

        // Taking over the remaining contributors is the only way to decorate items we do
        // not produce ourselves. Every result is passed along, decorated or not.
        result.runRemainingContributors(parameters) { completionResult ->
            val decorated = EsdsLookupDecorator.decorate(completionResult.lookupElement, category)
            result.passResult(completionResult.withLookupElement(decorated))
        }
    }

    /** Default size previews after `EsdsIcons.`, then each size's preview and dp size after `EsdsIcons.Plus.`. */
    private fun decorateIcons(parameters: CompletionParameters, result: CompletionResultSet, text: CharSequence) {
        val project = parameters.position.project
        val icon = EsdsIconMatcher.detectIconBeforeCaret(text, parameters.offset)
        if (icon == null && !EsdsIconMatcher.isCompletingIcon(text, parameters.offset)) return

        result.runRemainingContributors(parameters) { completionResult ->
            val element = completionResult.lookupElement
            val decorated = if (icon != null) {
                element.lookupString.takeIf { it in EsdsIconMatcher.SIZES }
                    ?.let { size -> EsdsIconRepository.icon(project, icon, size) }
                    ?.let { EsdsLookupDecorator.withValue(element, it.sizeLabel, it.Preview(16)) }
            } else {
                EsdsIconRepository.icon(project, element.lookupString, EsdsIconMatcher.DEFAULT_SIZE)
                    ?.let { EsdsLookupDecorator.withValue(element, value = null, icon = it.Preview(16)) }
            }
            result.passResult(completionResult.withLookupElement(decorated ?: element))
        }
    }
}
