package com.infomaniak.esdscompanion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.PropertyResourceBundle

/**
 * Guards the wiring that only shows up at runtime: a wrong extension point name, a bundle typo or
 * a bad implementation class would leave the plugin silently broken, or the settings entry showing
 * a raw resource key.
 */
class PluginRegistrationTest {

    private val pluginXml: String =
        checkNotNull(javaClass.getResourceAsStream("/META-INF/plugin.xml")) { "plugin.xml not found" }
            .bufferedReader()
            .readText()

    @Test
    fun `completion contributor is registered for kotlin, not for any`() {
        val registration = extension("completion.contributor")

        // `any` contributors run after every language-specific one, so they cannot decorate
        // the items produced by the Kotlin plugin.
        assertEquals("kotlin", registration.attribute("language"))
        assertEquals("first", registration.attribute("order"))
        assertClassExists(registration.attribute("implementationClass"))
    }

    @Test
    fun `inlay hints provider is registered and opt-in`() {
        val registration = extension("codeInsight.declarativeInlayProvider")

        assertEquals("kotlin", registration.attribute("language"))
        assertEquals("false", registration.attribute("isEnabledByDefault"))
        assertClassExists(registration.attribute("implementationClass"))
    }

    @Test
    fun `inlay hints settings labels resolve to real bundle entries`() {
        val registration = extension("codeInsight.declarativeInlayProvider")
        val bundlePath = registration.attribute("bundle").replace('.', '/') + ".properties"

        val bundle = checkNotNull(javaClass.getResourceAsStream("/$bundlePath")) {
            "Bundle $bundlePath is missing"
        }.use(::PropertyResourceBundle)

        for (key in listOf("nameKey", "descriptionKey")) {
            val resourceKey = registration.attribute(key)
            assertTrue(bundle.containsKey(resourceKey), "Bundle has no entry for $key='$resourceKey'")
            assertTrue(bundle.getString(resourceKey).isNotBlank(), "Empty bundle entry for '$resourceKey'")
        }
    }

    @Test
    fun `inlay click handler is registered with the id used by the hints`() {
        val registration = extension("codeInsight.inlayActionHandler")

        assertEquals(EsdsTokenNavigationHandler.HANDLER_ID, registration.attribute("handlerId"))
        assertClassExists(registration.attribute("implementationClass"))
    }

    private fun extension(name: String): String {
        val tag = Regex("""<$name\b[^>]*/>""", RegexOption.DOT_MATCHES_ALL).find(pluginXml)
        return checkNotNull(tag) { "No <$name> registration in plugin.xml" }.value
    }

    private fun String.attribute(name: String): String {
        val value = Regex("""$name\s*=\s*"([^"]*)"""").find(this)
        return checkNotNull(value) { "Attribute '$name' missing from: $this" }.groupValues[1]
    }

    private fun assertClassExists(className: String) {
        assertNotNull(Class.forName(className), "Class $className does not exist")
    }
}
