package com.infomaniak.dstokeninfo

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet

/**
 * Replaces the type text shown on the right of a completion item with the concrete value
 * of the design system token, so `EsdsTheme.icon.sizeSm` reads `sizeSm    20dp` instead of
 * the useless `sizeSm    Dp`.
 *
 * Registered with `order="first"` so it can wrap the items produced by the Kotlin plugin.
 */
class EsdsTokenCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        if (!file.name.endsWith(".kt") && !file.name.endsWith(".kts")) return

        val category = EsdsTokenMatcher.detectCategoryBeforeCaret(file.viewProvider.contents, parameters.offset) ?: return

        // Taking over the remaining contributors is the only way to decorate items we do
        // not produce ourselves. Every result is passed along, decorated or not.
        result.runRemainingContributors(parameters) { completionResult ->
            val decorated = EsdsLookupDecorator.decorate(completionResult.lookupElement, category)
            result.passResult(completionResult.withLookupElement(decorated))
        }
    }
}
