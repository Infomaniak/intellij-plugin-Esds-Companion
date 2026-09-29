package com.infomaniak.esdscompanion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame

class EsdsLookupDecoratorTest {

    private fun render(token: String, category: String): LookupElementPresentation {
        val original = LookupElementBuilder.create(token).withTypeText("Dp", true)
        val decorated = EsdsLookupDecorator.decorate(original, category)
        return LookupElementPresentation().also(decorated::renderElement)
    }

    @Test
    fun `shows the resolved value instead of the declared type`() {
        val presentation = render("sizeSm", "icon")

        assertEquals("sizeSm", presentation.itemText)
        assertEquals("20dp", presentation.typeText)
        assertFalse(presentation.isTypeGrayed)
    }

    @Test
    fun `decorates spacing and radius tokens too`() {
        assertEquals("8dp", render("md", "spacing").typeText)
        assertEquals("100dp", render("eightXl", "spacing").typeText)
        assertEquals("full", render("full", "radius").typeText)
    }

    @Test
    fun `leaves unknown tokens untouched`() {
        val original = LookupElementBuilder.create("somethingElse")

        assertSame(original, EsdsLookupDecorator.decorate(original, "icon"))
    }
}
