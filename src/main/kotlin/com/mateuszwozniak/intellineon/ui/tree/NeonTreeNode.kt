package com.mateuszwozniak.intellineon.ui.tree

import com.mateuszwozniak.intellineon.model.NeonAccount
import com.mateuszwozniak.intellineon.model.NeonBranch
import com.mateuszwozniak.intellineon.model.NeonOrganization
import com.mateuszwozniak.intellineon.model.NeonProject

sealed class NeonTreeNode(val displayText: String) {
    class Account(val account: NeonAccount) : NeonTreeNode(account.displayName)
    class Organization(val org: NeonOrganization) : NeonTreeNode(org.name)
    class Project(val project: NeonProject) : NeonTreeNode(project.name)
    class Branch(
        val branch: NeonBranch,
        val accountId: String,
        val accountName: String,
        val projectName: String
    ) : NeonTreeNode(branch.name)

    data object Loading : NeonTreeNode("Loading...")
    class Error(val message: String) : NeonTreeNode(message)
}
