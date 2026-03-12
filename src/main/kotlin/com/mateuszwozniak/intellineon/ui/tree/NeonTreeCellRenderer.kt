package com.mateuszwozniak.intellineon.ui.tree

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class NeonTreeCellRenderer : ColoredTreeCellRenderer() {

    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        val node = value as? DefaultMutableTreeNode ?: return
        when (val treeNode = node.userObject) {
            is NeonTreeNode.Account -> {
                icon = AllIcons.General.User
                append(treeNode.displayText)
                append("  ${treeNode.account.email}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                if (treeNode.account.error != null) {
                    append("  Errored", SimpleTextAttributes.ERROR_ATTRIBUTES)
                }
            }

            is NeonTreeNode.Organization -> {
                icon = AllIcons.Nodes.Module
                append(treeNode.displayText)
            }

            is NeonTreeNode.Project -> {
                icon = AllIcons.Nodes.Project
                append(treeNode.displayText)
            }

            is NeonTreeNode.Branch -> {
                icon = AllIcons.Vcs.Branch
                append(treeNode.displayText)
            }

            is NeonTreeNode.Loading -> {
                icon = AllIcons.Process.Step_1
                append(treeNode.displayText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }

            is NeonTreeNode.Error -> {
                icon = AllIcons.General.Error
                append(treeNode.displayText, SimpleTextAttributes.ERROR_ATTRIBUTES)
            }

            else -> append(value.toString())
        }
    }
}
