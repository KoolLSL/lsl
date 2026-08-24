package io.github.riej.lsl.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.Service

import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)

@State(
    name = "LslSettings",
    storages = [Storage("lsl_settings.xml")]
)
class LslSettingsState : PersistentStateComponent<LslSettingsState> {

    var optimizeConstants: Boolean = true
    var formatOnSave: Boolean = false
    var indentSize: Int = 4

    override fun getState(): LslSettingsState = this

    override fun loadState(state: LslSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: LslSettingsState
            get() = ApplicationManager.getApplication().getService(LslSettingsState::class.java)
    }
}
