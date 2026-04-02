package com.mateuszwozniak.intellineon.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import com.mateuszwozniak.intellineon.model.NeonAccount
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.mateuszwozniak.intellineon.repository.DataSourceRepository
import com.mateuszwozniak.intellineon.service.EnvironmentFileService
import com.mateuszwozniak.intellineon.service.NeonService
import com.mateuszwozniak.intellineon.ui.tree.NeonTreeCellRenderer
import com.mateuszwozniak.intellineon.ui.tree.NeonTreeNode
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class NeonExplorerPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val neonService = service<NeonService>()
    private val envService = project.service<EnvironmentFileService>()
    private val rootNode = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = NeonTreeCellRenderer()

        tree.emptyText.text = "No Neon accounts"
        tree.emptyText.appendLine("Add new account", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
            onAddAccount()
        }

        setupPopupMenu()

        val toolbar = createToolbar()
        add(toolbar.component, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(tree, SideBorder.NONE), BorderLayout.CENTER)

        reload()
    }

    private fun createToolbar(): ActionToolbar {
        val group = DefaultActionGroup()

        group.add(object : AnAction("Add Account", "Add a Neon account", AllIcons.General.Add) {
            override fun actionPerformed(e: AnActionEvent) {
                onAddAccount()
            }
        })

        group.add(object : AnAction("Remove Account", "Remove selected account", AllIcons.General.Remove) {
            override fun actionPerformed(e: AnActionEvent) {
                onRemoveAccount()
            }

            override fun update(e: AnActionEvent) {
                val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
                e.presentation.isEnabled = selectedNode?.userObject is NeonTreeNode.Account
            }
        })

        group.add(object : AnAction("Reload", "Reload Neon resources", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                onReload()
            }
        })

        group.addSeparator()

        group.add(object : AnAction("Expand All", "Expand all tree nodes", AllIcons.Actions.Expandall) {
            override fun actionPerformed(e: AnActionEvent) {
                expandAllNodes()
            }
        })

        group.add(object : AnAction("Collapse All", "Collapse all tree nodes", AllIcons.Actions.Collapseall) {
            override fun actionPerformed(e: AnActionEvent) {
                collapseAllNodes()
            }
        })

        val toolbar = ActionManager.getInstance().createActionToolbar("NeonExplorer", group, true)
        toolbar.targetComponent = this
        return toolbar
    }

    private fun onAddAccount() {
        val dialog = AccountDialog(project)
        if (dialog.showAndGet()) {
            val apiKey = dialog.getApiKey()
            if (apiKey.isBlank()) return
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    neonService.addAccount(apiKey)
                    reload()
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        Messages.showErrorDialog(project, e.message ?: "Unknown error", "Failed to Add Account")
                    }
                }
            }
        }
    }

    private fun onRemoveAccount() {
        val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val accountNode = selectedNode.userObject as? NeonTreeNode.Account ?: return
        val confirmed = Messages.showYesNoDialog(
            project,
            "Remove account '${accountNode.account.displayName}'?",
            "Remove Account",
            Messages.getQuestionIcon()
        )
        if (confirmed == Messages.YES) {
            neonService.removeAccount(accountNode.account.id)
            reload()
        }
    }

    private fun setupPopupMenu() {
        tree.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: Component, x: Int, y: Int) {
                val path = tree.getPathForLocation(x, y) ?: return
                tree.selectionPath = path
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val treeNode = node.userObject as? NeonTreeNode ?: return

                val group = DefaultActionGroup()

                val openAction = object : AnAction("Open In Dashboard", "Open in Neon Console", AllIcons.General.Web) {
                    override fun actionPerformed(e: AnActionEvent) {
                        when (treeNode) {
                            is NeonTreeNode.Account -> neonService.open()
                            is NeonTreeNode.Organization -> neonService.open(organization = treeNode.org)
                            is NeonTreeNode.Project -> neonService.open(project = treeNode.project)
                            is NeonTreeNode.Branch -> neonService.open(branch = treeNode.branch)
                            else -> return
                        }
                    }
                }
                group.add(openAction)

                if (treeNode is NeonTreeNode.Branch) {
                    if (isDatabasePluginAvailable()) {
                        group.add(object : AnAction(
                            "Connect To Branch",
                            "Create IntelliJ data source for this branch",
                            AllIcons.Actions.Lightning
                        ) {
                            override fun actionPerformed(e: AnActionEvent) {
                                onConnectToBranch(treeNode)
                            }
                        })
                    }
                    group.add(object : AnAction(
                        "Copy Pooled Connection String",
                        "Copy pooled connection string to clipboard",
                        AllIcons.Actions.Copy
                    ) {
                        override fun actionPerformed(e: AnActionEvent) {
                            onCopyConnectionString(treeNode, pooled = true)
                        }
                    })
                    group.add(object : AnAction(
                        "Copy Unpooled Connection String",
                        "Copy unpooled connection string to clipboard",
                        AllIcons.Actions.Copy
                    ) {
                        override fun actionPerformed(e: AnActionEvent) {
                            onCopyConnectionString(treeNode, pooled = false)
                        }
                    })
                    if (envService.hasEnvFile()) {
                        group.add(object : AnAction(
                            "Put Into .env",
                            "Upsert DATABASE_URL and DATABASE_URL_UNPOOLED/DATABASE_URL_DIRECT in .env file",
                            AllIcons.FileTypes.Any_type
                        ) {
                            override fun actionPerformed(e: AnActionEvent) {
                                onPutIntoEnv(treeNode)
                            }
                        })
                    }
                }

                val popup = ActionManager.getInstance().createActionPopupMenu("NeonExplorerPopup", group)
                popup.component.show(comp, x, y)
            }
        })
    }

    private fun isDatabasePluginAvailable(): Boolean {
        return PluginManagerCore.isPluginInstalled(PluginId.getId("com.intellij.database"))
    }

    private fun onConnectToBranch(branchNode: NeonTreeNode.Branch) {
        if (!isDatabasePluginAvailable()) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val uri = neonService.fetchConnectionUri(branchNode.accountId, branchNode.branch, pooled = false)
                val name = "neon-${branchNode.accountName}-${branchNode.projectName}-${branchNode.branch.name}"
                SwingUtilities.invokeLater {
                    DataSourceRepository(project).upsertDataSource(name, uri)
                    ToolWindowManager.getInstance(project).getToolWindow("Database")?.show()
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Intellineon Notifications")
                        .createNotification("Data source connected", name, NotificationType.INFORMATION)
                        .notify(project)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(project, e.message ?: "Unknown error", "Connect To Branch")
                }
            }
        }
    }

    private fun onPutIntoEnv(branchNode: NeonTreeNode.Branch) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val pooledUri = neonService.fetchConnectionUri(branchNode.accountId, branchNode.branch, pooled = true)
                val unpooledUri = neonService.fetchConnectionUri(branchNode.accountId, branchNode.branch, pooled = false)
                SwingUtilities.invokeLater {
                    val unpooledKey = envService.unpooledUrlKey()
                    envService.upsertEntries(mapOf(
                        "DATABASE_URL" to pooledUri,
                        unpooledKey to unpooledUri
                    ))
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Intellineon Notifications")
                        .createNotification(
                            "DATABASE_URL and $unpooledKey updated in .env",
                            NotificationType.INFORMATION
                        )
                        .notify(project)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(project, e.message ?: "Unknown error", "Put Into .env")
                }
            }
        }
    }

    private fun onCopyConnectionString(branchNode: NeonTreeNode.Branch, pooled: Boolean) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                neonService.copyConnectionString(branchNode.accountId, branchNode.branch, pooled)
                val label = if (pooled) "Pooled" else "Unpooled"
                SwingUtilities.invokeLater {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Intellineon Notifications")
                        .createNotification(
                            "$label connection string copied to clipboard",
                            NotificationType.INFORMATION
                        )
                        .notify(project)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(project, e.message ?: "Unknown error", "Copy Connection String")
                }
            }
        }
    }

    private fun expandAllNodes() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    private fun collapseAllNodes() {
        var row = tree.rowCount - 1
        while (row > 0) {
            tree.collapseRow(row)
            row--
        }
    }

    private fun onReload() {
        reload()
    }

    private fun reload() {
        setLoadingState()
        ApplicationManager.getApplication().executeOnPooledThread {
            val accounts = neonService.loadAccounts()
            SwingUtilities.invokeLater {
                populateTree(accounts)
            }
        }
    }

    private fun setLoadingState() {
        rootNode.removeAllChildren()
        rootNode.add(DefaultMutableTreeNode(NeonTreeNode.Loading))
        treeModel.reload()
    }

    private fun populateTree(accounts: List<NeonAccount>) {
        rootNode.removeAllChildren()
        for (account in accounts) {
            val accountNode = DefaultMutableTreeNode(NeonTreeNode.Account(account))
            for (org in account.organizations) {
                val orgNode = DefaultMutableTreeNode(NeonTreeNode.Organization(org))
                for (project in org.projects) {
                    val projectNode = DefaultMutableTreeNode(NeonTreeNode.Project(project))
                    for (branch in project.branches) {
                        projectNode.add(
                            DefaultMutableTreeNode(
                                NeonTreeNode.Branch(branch, account.id, account.displayName, project.name)
                            )
                        )
                    }
                    orgNode.add(projectNode)
                }
                accountNode.add(orgNode)
            }
            rootNode.add(accountNode)
        }
        treeModel.reload()
    }
}
