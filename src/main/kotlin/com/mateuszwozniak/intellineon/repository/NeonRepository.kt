package com.mateuszwozniak.intellineon.repository

import com.intellij.openapi.diagnostic.Logger
import com.mateuszwozniak.intellineon.api.generated.apis.BranchApi
import com.mateuszwozniak.intellineon.api.generated.apis.ProjectApi
import com.mateuszwozniak.intellineon.api.generated.apis.UsersApi
import com.mateuszwozniak.intellineon.model.NeonAccount
import com.mateuszwozniak.intellineon.model.NeonBranch
import com.mateuszwozniak.intellineon.model.NeonOrganization
import com.mateuszwozniak.intellineon.model.NeonProject
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

class NeonRepository(apiKey: String) {

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $apiKey").build())
        }
        .build()

    private val usersApi = UsersApi(client = httpClient)
    private val projectApi = ProjectApi(client = httpClient)
    private val branchApi = BranchApi(client = httpClient)

    fun fetchAccount(): NeonAccount = runBlocking {
        val r = usersApi.getCurrentUserInfo()
        NeonAccount(
            id = r.id,
            displayName = r.name.ifBlank { r.login },
            email = r.email,
            organizations = fetchOrganizations()
        )
    }

    private fun fetchOrganizations(): List<NeonOrganization> {
        val orgs = try {
            runBlocking { usersApi.getCurrentUserOrganizations().organizations }
        } catch (e: Exception) {
            LOG.warn("Could not load organizations: ${e.message}")
            return emptyList()
        }
        return orgs.map { org ->
            NeonOrganization(
                id = org.id,
                name = org.name,
                projects = fetchProjects(org.id)
            )
        }
    }

    private fun fetchProjects(orgId: String): List<NeonProject> {
        val projects = try {
            runBlocking { projectApi.listProjects(orgId = orgId).projects }
        } catch (e: Exception) {
            LOG.warn("Could not load projects for org $orgId: ${e.message}")
            return emptyList()
        }
        return projects.map { project ->
            NeonProject(
                id = project.id,
                name = project.name,
                organizationId = project.orgId,
                branches = fetchBranches(project.id)
            )
        }
    }

    private fun fetchBranches(projectId: String): List<NeonBranch> = try {
        runBlocking {
            branchApi.listProjectBranches(projectId).branches
                .map { NeonBranch(id = it.id, name = it.name, projectId = it.projectId) }
        }
    } catch (e: Exception) {
        LOG.warn("Could not load branches for project $projectId: ${e.message}")
        emptyList()
    }

    fun fetchConnectionUri(projectId: String, branchId: String, pooled: Boolean): String = runBlocking {
        val databaseName = branchApi.listProjectBranchDatabases(projectId, branchId)
            .databases.firstOrNull()?.name ?: "neondb"
        val roleName = branchApi.listProjectBranchRoles(projectId, branchId)
            .roles.firstOrNull()?.name ?: "neondb_owner"
        projectApi.getConnectionURI(projectId, databaseName, roleName, branchId = branchId, pooled = pooled).uri
    }

    companion object {
        private val LOG = Logger.getInstance(NeonRepository::class.java)
    }
}
