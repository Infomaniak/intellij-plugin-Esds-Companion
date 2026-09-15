package com.infomaniak.dstokeninfo

import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.CancellablePromise

/**
 * Opens the design system source defining a token when its inlay hint is clicked.
 *
 * Resolving the file happens on click rather than while building the hint, because the lookup
 * hits the filename index and the hints pass runs on every edit. It is then done off the EDT,
 * since querying the index and reading a file out of a library jar are both slow operations.
 */
internal class EsdsTokenNavigationHandler : InlayActionHandler {

    override fun handleClick(event: EditorMouseEvent, payload: InlayActionPayload) {
        navigateTo(event.editor, payload)
    }

    /** Returns the pending lookup, so tests can await a click that is otherwise fire and forget. */
    fun navigateTo(editor: Editor, payload: InlayActionPayload): CancellablePromise<*>? {
        val project = editor.project ?: return null
        val reference = (payload as? StringInlayActionPayload)?.text ?: return null

        val (category, tokenName) = split(reference) ?: return null
        val token = EsdsTokenValues.tokenOf(category, tokenName) ?: return null

        return ReadAction.nonBlocking<EsdsTokenNavigator.Target?> {
            EsdsTokenNavigator.findDefinition(project, category, tokenName, token)
        }
            .inSmartMode(project)
            .expireWhen { editor.isDisposed || project.isDisposed }
            .finishOnUiThread(ModalityState.defaultModalityState()) { target ->
                navigate(project, editor, target, "$category.$tokenName")
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun navigate(project: Project, editor: Editor, target: EsdsTokenNavigator.Target?, token: String) {
        if (target == null) {
            // Doing nothing at all would just look like a broken hint, so explain why instead.
            HintManager.getInstance()
                .showErrorHint(editor, DsTokenInfoBundle.message("navigation.sourceNotFound", token))
            return
        }

        OpenFileDescriptor(project, target.file, target.offset).navigate(true)
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
