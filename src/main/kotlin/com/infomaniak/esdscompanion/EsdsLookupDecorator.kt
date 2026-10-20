package com.infomaniak.esdscompanion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import javax.swing.Icon

/**
 * Wraps a completion item so the type text on its right shows the token's resolved value
 * (`20dp`) instead of its declared type (`Dp`).
 */
internal object EsdsLookupDecorator {

    /** Returns [lookupElement] unchanged when it is not a known token of [category]. */
    fun decorate(lookupElement: LookupElement, category: String): LookupElement {
        val value = EsdsTokenValues.valueOf(category, lookupElement.lookupString) ?: return lookupElement
        return withValue(lookupElement, value)
    }

    /** Replaces the type text with [value] and, when given, the item icon with [icon]. */
    fun withValue(lookupElement: LookupElement, value: String?, icon: Icon? = null): LookupElement =
        LookupElementDecorator.withRenderer(lookupElement, ValueRenderer(value, icon))

    private class ValueRenderer(
        private val value: String?,
        private val icon: Icon?,
    ) : LookupElementRenderer<LookupElementDecorator<LookupElement>>() {

        override fun renderElement(
            element: LookupElementDecorator<LookupElement>,
            presentation: LookupElementPresentation,
        ) {
            element.delegate.renderElement(presentation)
            if (value != null) {
                presentation.typeText = value
                presentation.isTypeGrayed = false
            }
            if (icon != null) presentation.icon = icon
        }
    }
}
