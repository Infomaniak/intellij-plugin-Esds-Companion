package com.infomaniak.esdscompanion

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.ColorPanel
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import java.awt.Color

@Service
@State(name = "EsdsCompanionSettings", storages = [Storage("esdsCompanion.xml")])
internal class EsdsIconSettings : SimplePersistentStateComponent<EsdsIconSettings.Settings>(Settings()) {

    class Settings : BaseState() {
        var useCustomIconColor by property(false)
        var customIconColor by string("808080")
    }

    /** The editor text color by default, which already contrasts with both light and dark themes. */
    fun color(): Color =
        state.customIconColor?.takeIf { state.useCustomIconColor }?.let { ColorUtil.fromHex(it, null) } ?: JBColor.foreground()

    companion object {
        fun getInstance(): EsdsIconSettings = service()
    }
}

/** Settings | Tools | Esds Companion. */
internal class EsdsCompanionConfigurable : BoundConfigurable(EsdsCompanionBundle.message("settings.displayName")) {

    override fun createPanel() = panel {
        val settings = EsdsIconSettings.getInstance().state
        group(EsdsCompanionBundle.message("settings.icons")) {
            row {
                val custom = checkBox(EsdsCompanionBundle.message("settings.customIconColor"))
                    .bindSelected(settings::useCustomIconColor)
                cell(ColorPanel())
                    .bind(
                        ColorPanel::getSelectedColor,
                        ColorPanel::setSelectedColor,
                        MutableProperty(
                            { ColorUtil.fromHex(settings.customIconColor.orEmpty(), Color.GRAY) },
                            { color -> color?.let { settings.customIconColor = ColorUtil.toHex(it) } },
                        ),
                    )
                    .enabledIf(custom.selected)
            }.rowComment(EsdsCompanionBundle.message("settings.customIconColor.comment"))
        }
    }

    override fun apply() {
        super.apply()
        // Previews read the color when painted: a repaint is enough.
        EditorFactory.getInstance().refreshAllEditors()
    }
}
