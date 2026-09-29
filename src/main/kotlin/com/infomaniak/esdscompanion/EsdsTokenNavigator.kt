package com.infomaniak.esdscompanion

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Locates the design system source that defines a token's value, so clicking an inlay hint can
 * jump to it.
 *
 * The design system is always on the project's path (otherwise `EsdsTheme` would not resolve),
 * so the files are found by name through the filename index. That avoids resolving Kotlin
 * symbols, and therefore any dependency on the Kotlin plugin.
 */
internal object EsdsTokenNavigator {

    /**
     * [file] is kept as-is rather than as a path: the design system is usually a library, so the
     * file lives inside a jar and cannot be reached back through a `file://` URL.
     */
    data class Target(val fileName: String, val offset: Int, val file: VirtualFile)

    /** How the searched property is written in the file declaring it. */
    private enum class Style {
        /** `sizeSm = IntermediateDefault.IconSizeSm,` in the default theme. */
        ASSIGNMENT,

        /** `val IconSizeSm: Dp = Scale20` in the intermediate object and in the primitives. */
        DECLARATION,
    }

    private data class Candidate(val fileName: String, val propertyName: String, val style: Style)

    /**
     * Resolves where [tokenName] of [category] is defined, walking the indirection chain outwards
     * and stopping at the first file that is part of the project:
     *
     *  1. `DefaultIconTokens.kt`, which reads `sizeSm = IntermediateDefault.IconSizeSm`
     *  2. `IntermediateDefault.kt`, which reads `IconSizeSm = Scale20`
     *  3. `ScalePrimitiveTokens.kt`, which reads `Scale20 = 20.dp`
     *
     * The first is the most useful, being the default theme entry that names the token itself.
     */
    fun findDefinition(
        project: Project,
        category: String,
        tokenName: String,
        token: EsdsTokenValues.Token,
    ): Target? {
        val candidates = listOfNotNull(
            EsdsTokenValues.categoryFileNames[category]?.let { Candidate(it, tokenName, Style.ASSIGNMENT) },
            Candidate(EsdsTokenValues.DECLARATION_FILE_NAME, token.declarationName, Style.DECLARATION),
            Candidate(token.primitiveFileName, token.primitiveName, Style.DECLARATION),
        )

        return candidates.firstNotNullOfOrNull { findProperty(project, it) }
    }

    private fun findProperty(project: Project, candidate: Candidate): Target? {
        val scope = GlobalSearchScope.allScope(project)
        val files = FilenameIndex.getVirtualFilesByName(candidate.fileName, scope)
        val regex = regexFor(candidate)

        for (file in files) {
            val text = runCatching { String(file.contentsToByteArray(), file.charset) }.getOrNull() ?: continue
            val offset = regex.find(text)?.groups?.get(1)?.range?.first ?: continue
            return Target(candidate.fileName, offset, file)
        }
        return null
    }

    private fun regexFor(candidate: Candidate): Regex {
        val name = Regex.escape(candidate.propertyName)
        return when (candidate.style) {
            // Not preceded by a dot, so `sizeSm =` matches but `Foo.sizeSm` does not.
            Style.ASSIGNMENT -> Regex("""(?<![.\w])($name)\s*=""")
            Style.DECLARATION -> Regex("""\bval\s+($name)\b""")
        }
    }
}
