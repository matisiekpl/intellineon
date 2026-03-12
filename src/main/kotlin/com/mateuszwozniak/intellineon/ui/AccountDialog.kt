package com.mateuszwozniak.intellineon.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class AccountDialog(project: Project?) : DialogWrapper(project, true) {

    private val apiKeyField = JBTextField(40)

    init {
        title = "Add Neon Account"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val descriptionLabel = JBLabel(
            "<html>To connect a new neon.com account, please generate an API key and paste it here.</html>"
        )
        descriptionLabel.alignmentX = JComponent.LEFT_ALIGNMENT
        panel.add(descriptionLabel)

        panel.add(javax.swing.Box.createVerticalStrut(JBUI.scale(6)))

        val linkPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        linkPanel.alignmentX = JComponent.LEFT_ALIGNMENT
        val linkButton = JButton("Generate API Key")
        linkButton.addActionListener {
            BrowserUtil.browse("https://console.neon.tech/app/settings#api-keys")
        }
        linkPanel.add(linkButton)
        panel.add(linkPanel)

        panel.add(javax.swing.Box.createVerticalStrut(JBUI.scale(12)))

        val fieldPanel = JPanel(BorderLayout(8, 0))
        fieldPanel.alignmentX = JComponent.LEFT_ALIGNMENT
        fieldPanel.add(JBLabel("API Key:"), BorderLayout.WEST)
        fieldPanel.add(apiKeyField, BorderLayout.CENTER)
        panel.add(fieldPanel)

        return panel
    }

    fun getApiKey(): String = apiKeyField.text.trim()
}
