package io.github.koollsl.lsl.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "LslSettings",
    storages = [Storage("lsl_settings.xml")]
)
class LslSettings : PersistentStateComponent<LslSettings> {

    var optimizeConstants: Boolean = true
    var formatOnSave: Boolean = false
    var indentSize: Int = 4
    var customKwdbPath: String = ""

    override fun getState(): LslSettings = this

    override fun loadState(state: LslSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: LslSettings
            get() = service()
    }
}