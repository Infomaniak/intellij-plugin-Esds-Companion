package com.infomaniak.esdscompanion

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement

/**
 * Shows a preview of `EsdsIcons.Plus.Medium` in the gutter (`EsdsIcons.Plus` shows the default size). Can be turned off under
 * Settings | Editor | General | Gutter Icons.
 */
class EsdsIconLineMarkerProvider : LineMarkerProviderDescriptor() {

    override fun getName(): String = EsdsCompanionBundle.message("gutter.esdsIcon.name")

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Anchored on the last identifier of `EsdsIcons.Plus` or `EsdsIcons.Plus.Medium`.
        if (element.firstChild != null || element.node.elementType.toString() != "IDENTIFIER") return null
        val reference = element.parent ?: return null
        val qualified = reference.parent ?: return null
        if (qualified.lastChild != reference || !qualified.isDotQualified()) return null
        // `EsdsIcons.Plus` inside `EsdsIcons.Plus.Medium`: the outer expression gets the marker.
        val outer = qualified.parent
        if (outer != null && outer.isDotQualified() && EsdsIconMatcher.resolveExpression(outer.text) != null) return null

        val icon = EsdsIconMatcher.resolveExpression(qualified.text) ?: return null
        val vector = EsdsIconRepository.icon(element.project, icon.name, icon.size) ?: return null
        val tooltip = "${EsdsIconMatcher.ICONS_OBJECT}.${icon.name}.${icon.size} · ${vector.sizeLabel}"
        return LineMarkerInfo(element, element.textRange, vector.Preview(12), { tooltip }, null, GutterIconRenderer.Alignment.LEFT, { tooltip })
    }

    private fun PsiElement.isDotQualified() = node.elementType.toString() == "DOT_QUALIFIED_EXPRESSION"
}
