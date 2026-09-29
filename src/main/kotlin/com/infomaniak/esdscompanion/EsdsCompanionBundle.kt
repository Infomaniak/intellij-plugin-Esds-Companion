package com.infomaniak.esdscompanion

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.EsdsCompanionBundle"

/** Messages shown to the user, shared with the declarations in `plugin.xml`. */
internal object EsdsCompanionBundle : DynamicBundle(BUNDLE) {

    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
