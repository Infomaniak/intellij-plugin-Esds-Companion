package com.infomaniak.dstokeninfo

import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Opens the design system source defining a token when its inlay hint is clicked.
 *
 * Resolving the file happens on click rather than while building the hint, because the lookup
 * hits the filename index and the hints pass runs on every edit.
 */
internal class EsdsTokenNavigationHandler : InlayActionHandler {

    override fun handleClick(event: EditorMouseEvent, payload: InlayActionPayload) {
        val project = event.editor.project ?: return
        val reference = (payload as? StringInlayActionPayload)?.text ?: return

        val (category, tokenName) = split(reference) ?: return
        val token = EsdsTokenValues.tokenOf(category, tokenName) ?: return
        val target = EsdsTokenNavigator.findDefinition(project, category, tokenName, token) ?: return
        val file = VirtualFileManager.getInstance().findFileByUrl(VirtualFileManager.constructUrl("file", target.filePath))
            ?: return

        OpenFileDescriptor(project, file, target.offset).navigate(true)
    }

    private fun split(reference: String): Pair<String, String>? {
        val parts = reference.split(SEPARATOR, limit = 2).takeIf { it.size == 2 } ?: return null
        return parts[0] to parts[1]
    }

    companion object {
        const val HANDLER_ID: String = "com.infomaniak.dstokeninfo.navigateToToken"

        private const val SEPARATOR = "/"

        /** Builds the payload consumed by [handleClick]. */
        fun payloadFor(category: String, token: String): StringInlayActionPayload =
            StringInlayActionPayload("$category$SEPARATOR$token")
    }
}
