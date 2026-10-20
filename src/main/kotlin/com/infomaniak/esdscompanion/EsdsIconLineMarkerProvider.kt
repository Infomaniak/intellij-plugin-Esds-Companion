package com.infomaniak.esdscompanion

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement

/**
 * Shows a preview of `EsdsIcons.Medium.Plus` in the gutter. Can be turned off under
 * Settings | Editor | General | Gutter Icons.
 */
class EsdsIconLineMarkerProvider : LineMarkerProviderDescriptor() {

    override fun getName(): String = EsdsCompanionBundle.message("gutter.esdsIcon.name")

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Anchored on the icon name, the last identifier of `EsdsIcons.Medium.Plus`.
        if (element.firstChild != null || element.node.elementType.toString() != "IDENTIFIER") return null
        val reference = element.parent ?: return null
        val qualified = reference.parent ?: return null
        if (qualified.lastChild != reference || qualified.node.elementType.toString() != "DOT_QUALIFIED_EXPRESSION") return null

        val icon = EsdsIconMatcher.resolveExpression(qualified.text)?.takeIf { it.name == element.text } ?: return null
        val vector = EsdsIconRepository.icon(element.project, icon.size, icon.name) ?: return null
        val tooltip = "${EsdsIconMatcher.ICONS_OBJECT}.${icon.size}.${icon.name} · ${vector.sizeLabel}"
        return LineMarkerInfo(element, element.textRange, vector.Preview(12), { tooltip }, null, GutterIconRenderer.Alignment.LEFT, { tooltip })
    }
}
