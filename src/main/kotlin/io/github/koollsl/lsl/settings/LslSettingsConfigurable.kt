package io.github.koollsl.lsl.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class LslSettingsConfigurable : Configurable {

    private val settings = LslSettings.instance
    private var panel: DialogPanel? = null

    override fun getDisplayName(): String = "LSL Settings"

    override fun createComponent(): JComponent {
        val createdPanel = panel {
            row {
                checkBox("Constant optimization")
                    .bindSelected(settings::optimizeConstants)
                    .comment("""
                    Inlines constant values and pre-calculates math.<br>
                    Example: <code>integer HOUR = 3600; llSetTimer(24 * HOUR);<br>
                    becomes: llSetTimer(86400);</code></pre><br>
                    Enable for release to reduce scripts memory; disable when debugging.
                     """.trimIndent())
            }

            separator()

            row {
                text("""
                    To update the Keyword Database (LSL functions, events, and constants), edit or download a newer <code>kwdb.xml</code> file from <a href="https://github.com/Sei-Lisa/kwdb">github.com/Sei-Lisa/kwdb</a> and select it below.<br>
                    <i>Leave blank to use the plugin's original definitions. Restart IDE after changing.</i>
                """.trimIndent())
            }

            row("Custom kwdb.xml:") {
                val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("xml")
                    .withTitle("Select kwdb.xml file")
                    .withDescription("Select kwdb.xml file")

                textFieldWithBrowseButton(
                    fileChooserDescriptor = descriptor
                )
                    .bindText(settings::customKwdbPath)
                    .columns(COLUMNS_LARGE)
            }

            separator()

            row {
                text("""
                    <b>Related Settings</b><br>
                    • To customize formatting rules, go to <b>Editor | Code Style | LSL</b>.<br>
                    • To adjust syntax highlighting, go to <b>Editor | Color Scheme | LSL</b>.
                """.trimIndent())
            }
        }

        panel = createdPanel
        return createdPanel
    }

    override fun isModified(): Boolean = panel?.isModified() ?: false

    override fun apply() {
        panel?.apply()
    }

    override fun reset() {
        panel?.reset()
    }
}