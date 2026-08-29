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
                    Inlines constant values and pre-calculates math.
                    Example: <code>integer SECONDS_PER_HOUR = 3600; llSetTimer(24 * SECONDS_PER_HOUR);
                    becomes: llSetTimer(86400);</code></pre>
                    Enable for release scripts to reduce memory usage; disable when debugging.
                     """.trimIndent())
            }

            separator()

            row {
                text("""
                    <b>Custom Keyword Database (kwdb.xml)</b><br>
                    To update LSL functions, events, and constants without waiting for a plugin release, download a newer <code>kwdb.xml</code> from <a href="https://github.com/Sei-Lisa/kwdb">GitHub (Sei-Lisa/kwdb)</a> and select it below.<br>
                    <i>Leave blank to use the plugin's original definitions. Restart IDE after changing.</i>
                """.trimIndent())
            }

            row("Custom kwdb.xml path:") {
                val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("xml")
                    .withTitle("Select KWDB File")
                    .withDescription("Select your kwdb.xml file")

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