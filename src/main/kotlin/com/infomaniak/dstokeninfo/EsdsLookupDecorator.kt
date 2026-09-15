package com.infomaniak.dstokeninfo

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer

/**
 * Wraps a completion item so the type text on its right shows the token's resolved value
 * (`20dp`) instead of its declared type (`Dp`).
 */
internal object EsdsLookupDecorator {

    /** Returns [lookupElement] unchanged when it is not a known token of [category]. */
    fun decorate(lookupElement: LookupElement, category: String): LookupElement {
        val value = EsdsTokenValues.valueOf(category, lookupElement.lookupString) ?: return lookupElement
        return LookupElementDecorator.withRenderer(lookupElement, ValueRenderer(value))
    }

    private class ValueRenderer(
        private val value: String,
    ) : LookupElementRenderer<LookupElementDecorator<LookupElement>>() {

        override fun renderElement(
            element: LookupElementDecorator<LookupElement>,
            presentation: LookupElementPresentation,
        ) {
            element.delegate.renderElement(presentation)
            presentation.typeText = value
            presentation.isTypeGrayed = false
        }
    }
}
