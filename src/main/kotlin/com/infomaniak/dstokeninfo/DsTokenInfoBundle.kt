package com.infomaniak.dstokeninfo

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.DsTokenInfoBundle"

/** Messages shown to the user, shared with the declarations in `plugin.xml`. */
internal object DsTokenInfoBundle : DynamicBundle(BUNDLE) {

    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
