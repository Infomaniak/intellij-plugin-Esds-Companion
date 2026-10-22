package com.infomaniak.esdscompanion

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the design system icons compiled into the project's dependencies, such as
 * `com/infomaniak/designsystem/icons/imagevector/medium/PlusKt.class` for `EsdsIcons.Plus.Medium`.
 */
internal object EsdsIconRepository {

    private const val ICONS_PACKAGE = "com/infomaniak/designsystem/icons"

    /** Keyed by class file URL, which includes the design system version. */
    private val cache = ConcurrentHashMap<String, EsdsIconVector>()

    /** Icon [name] (`Plus`) drawn at [size] (`Medium`), from the internal `EsdsIcons.Medium.Plus` getter. */
    fun icon(project: Project, name: String, size: String): EsdsIconVector? {
        val file = iconsDirectory(project)?.findFileByRelativePath("imagevector/${size.lowercase()}/${name}Kt.class")
        return file?.let { read(it, name) }
    }

    /** The size every icon of [size] (`Small`) is designed for, such as `20dp`. */
    fun sizeLabel(project: Project, size: String): String? {
        val sizeDirectory = iconsDirectory(project)?.findFileByRelativePath("imagevector/${size.lowercase()}") ?: return null
        val anyIcon = sizeDirectory.children.firstOrNull { it.name.endsWith("Kt.class") } ?: return null
        return read(anyIcon, anyIcon.name.removeSuffix("Kt.class"))?.sizeLabel
    }

    private fun read(file: VirtualFile, name: String): EsdsIconVector? =
        cache[file.url] ?: runCatching { file.contentsToByteArray() }.getOrNull()
            ?.let { EsdsIconVector.read(it, name) }
            ?.also { cache[file.url] = it }

    private fun iconsDirectory(project: Project): VirtualFile? =
        CachedValuesManager.getManager(project).getCachedValue(project) {
            val directory = OrderEnumerator.orderEntries(project).withoutSdk().classes().roots.firstNotNullOfOrNull { root ->
                root.findFileByRelativePath(ICONS_PACKAGE)?.takeIf { it.findChild("EsdsIcons.class") != null }
            }
            CachedValueProvider.Result.create(directory, ProjectRootModificationTracker.getInstance(project))
        }
}
