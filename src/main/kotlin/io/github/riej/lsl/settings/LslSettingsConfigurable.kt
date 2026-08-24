package io.github.riej.lsl.settings

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JPanel

class LslSettingsConfigurable : Configurable {

    private lateinit var panel: JComponent
    private lateinit var optimizeConstants: JBCheckBox

    override fun getDisplayName(): String = "LSL Settings"

    override fun createComponent(): JComponent {
        val settings = LslSettingsState.instance

        optimizeConstants = JBCheckBox("Enable constant optimization", settings.optimizeConstants)

        val kwdbPath = PathManager.getOptionsPath()
        val kwdbInfoLabel = JBLabel(
            "<html>" +
                    "<b>Custom Keyword Database (kwdb.xml):</b><br>" +
                    "To override default LSL constants, events, and functions, place your custom <code>kwdb.xml</code> file in:<br>" +
                    "<code>$kwdbPath</code><br>" +
                    "<i>Restart the IDE after replacing the file to reload definitions.</i>" +
                    "</html>"
        ).apply {
            componentStyle = UIUtil.ComponentStyle.SMALL
            fontColor = UIUtil.FontColor.BRIGHTER
        }

        // Hardcoded string targets
        val codeStyleLink = HyperlinkLabel("Configure Code Style (LSL)...").apply {
            addHyperlinkListener {
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    null,
                    "preferences.sourceCode.LSL"
                )
            }
        }

        val colorSchemeLink = HyperlinkLabel("Configure Colors & Fonts (LSL)...").apply {
            addHyperlinkListener {
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    null,
                    "reference.settingsdialog.IDE.editor.colors.LSL"
                )
            }
        }

        panel = FormBuilder.createFormBuilder()
            .addComponent(optimizeConstants)
            .addSeparator()
            .addComponent(kwdbInfoLabel)
            .addSeparator()
            .addComponent(codeStyleLink)
            .addComponent(colorSchemeLink)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return panel
    }

    override fun isModified(): Boolean =
        LslSettingsState.instance.optimizeConstants != optimizeConstants.isSelected

    override fun apply() {
        LslSettingsState.instance.optimizeConstants = optimizeConstants.isSelected
    }

    override fun reset() {
        optimizeConstants.isSelected = LslSettingsState.instance.optimizeConstants
    }
}