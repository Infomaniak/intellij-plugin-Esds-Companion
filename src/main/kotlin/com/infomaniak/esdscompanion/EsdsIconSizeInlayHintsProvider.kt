package com.infomaniak.esdscompanion

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement

/**
 * Shows the size of an icon next to `EsdsIcons.Plus.Small`, or `icon.Small` on any `Symbol`: `20dp`.
 *
 * Switched on and off under *Settings | Editor | Inlay Hints | Values | Infomaniak design system icon sizes*.
 */
internal class EsdsIconSizeInlayHintsProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector = Collector()

    private class Collector : SharedBypassCollector {

        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            // The `Small` selector of a dot-qualified expression. Matching names first keeps resolving rare.
            if (element.node?.elementType?.toString() != "REFERENCE_EXPRESSION") return
            val size = element.text.takeIf { it in EsdsIconMatcher.SIZES } ?: return
            val qualified = element.parent ?: return
            if (qualified.lastChild != element || qualified.node?.elementType?.toString() != "DOT_QUALIFIED_EXPRESSION") return

            // Resolving tells `symbol.Small` apart from an unrelated `TextSize.Small`, whatever the receiver.
            val declaration = element.references.firstNotNullOfOrNull { it.resolve() } ?: return
            if ((declaration.parent?.parent as? PsiNamedElement)?.name != SYMBOL_INTERFACE) return

            val label = EsdsIconRepository.sizeLabel(element.project, size) ?: return
            sink.addPresentation(
                position = InlineInlayPosition(qualified.textRange.endOffset, relatedToPrevious = true),
                hintFormat = HintFormat.default,
            ) {
                text(label)
            }
        }
    }

    private companion object {
        /** `Symbol`, declaring `val Small: ImageVector` and the other sizes. */
        const val SYMBOL_INTERFACE = "Symbol"
    }
}
