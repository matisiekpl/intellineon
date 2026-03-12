package com.mateuszwozniak.intellineon.service

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.BrowserUtil
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.mateuszwozniak.intellineon.model.NeonAccount
import com.mateuszwozniak.intellineon.model.NeonBranch
import com.mateuszwozniak.intellineon.model.NeonOrganization
import com.mateuszwozniak.intellineon.model.NeonProject
import com.mateuszwozniak.intellineon.repository.NeonRepository

@State(name = "IntellineonAccounts", storages = [Storage("intellineon-accounts.xml")])
@Service(Service.Level.APP)
class NeonService : PersistentStateComponent<NeonService.State> {

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun addAccount(apiKey: String): NeonAccount {
        val account = NeonRepository(apiKey).fetchAccount()

        PasswordSafe.instance.set(
            credentialAttributes(account.id),
            Credentials(account.id, apiKey)
        )

        state.accounts.add(AccountEntry().apply {
            id = account.id
            displayName = account.displayName
            email = account.email
        })

        return account
    }

    fun open(
        organization: NeonOrganization? = null,
        project: NeonProject? = null,
        branch: NeonBranch? = null
    ) {
        val url = when {
            branch != null -> "https://console.neon.tech/app/projects/${branch.projectId}/branches/${branch.id}"
            project != null -> "https://console.neon.tech/app/projects/${project.id}"
            organization != null -> "https://console.neon.tech/app/${organization.id}/projects"
            else -> "https://console.neon.tech/app/"
        }
        BrowserUtil.browse(url)
    }

    fun fetchConnectionUri(accountId: String, branch: NeonBranch, pooled: Boolean): String {
        val apiKey = PasswordSafe.instance.getPassword(credentialAttributes(accountId))
            ?: error("API key not found for account $accountId")
        return NeonRepository(apiKey).fetchConnectionUri(branch.projectId, branch.id, pooled)
    }

    fun copyConnectionString(accountId: String, branch: NeonBranch, pooled: Boolean): String {
        val uri = fetchConnectionUri(accountId, branch, pooled)
        CopyPasteManager.getInstance().setContents(StringSelection(uri))
        return uri
    }

    fun removeAccount(accountId: String) {
        PasswordSafe.instance.set(credentialAttributes(accountId), null)
        state.accounts.removeIf { it.id == accountId }
    }

    fun loadAccounts(): List<NeonAccount> {
        return state.accounts.mapNotNull { entry ->
            val apiKey = PasswordSafe.instance.getPassword(credentialAttributes(entry.id))
                ?: return@mapNotNull null
            try {
                NeonRepository(apiKey).fetchAccount()
            } catch (e: Exception) {
                NeonAccount(id = entry.id, displayName = entry.displayName, email = entry.email, error = e.message)
            }
        }
    }

    private fun credentialAttributes(accountId: String) = CredentialAttributes(
        generateServiceName("Intellineon", accountId)
    )

    class State {
        @XCollection
        var accounts: MutableList<AccountEntry> = mutableListOf()
    }

    @Tag("account")
    class AccountEntry {
        @Attribute var id: String = ""
        @Attribute var displayName: String = ""
        @Attribute var email: String = ""
    }
}
