package com.infomaniak.esdscompanion

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Shows the value of a design system token inline, next to its call site:
 *
 *     Modifier.padding(EsdsTheme.spacing.md)  ->  Modifier.padding(EsdsTheme.spacing.md 8dp)
 *
 * Clicking a hint navigates to the design system source that defines the value.
 *
 * Users switch this on and off per-IDE under
 * *Settings | Editor | Inlay Hints | Values | Infomaniak design system token values*.
 */
internal class EsdsTokenInlayHintsProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector()

    private class Collector : SharedBypassCollector {

        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            // Matching the element type by name avoids a compile-time dependency on the Kotlin
            // plugin's PSI, which is what keeps this plugin version-independent.
            if (element.node?.elementType?.toString() != DOT_QUALIFIED_EXPRESSION) return

            // A nested receiver such as `EsdsTheme.icon` is a dot-qualified expression too, but
            // it is not a complete token reference, so it never matches and no hint is emitted
            // twice for the same expression.
            val reference = EsdsTokenMatcher.resolveExpression(element.text) ?: return

            // Clicking the hint jumps to the design system source defining the value.
            val navigation = InlayActionData(
                EsdsTokenNavigationHandler.payloadFor(reference.category, reference.token),
                EsdsTokenNavigationHandler.HANDLER_ID,
            )

            sink.addPresentation(
                position = InlineInlayPosition(element.textRange.endOffset, relatedToPrevious = true),
                hintFormat = HintFormat.default,
            ) {
                text(reference.value.value, navigation)
            }
        }
    }

    private companion object {
        const val DOT_QUALIFIED_EXPRESSION = "DOT_QUALIFIED_EXPRESSION"
    }
}
