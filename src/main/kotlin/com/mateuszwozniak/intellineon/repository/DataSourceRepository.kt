package com.mateuszwozniak.intellineon.repository

import com.intellij.credentialStore.OneTimeString
import com.intellij.database.access.DatabaseCredentials
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.openapi.project.Project
import java.net.URI

class DataSourceRepository(private val project: Project) {

    fun upsertDataSource(name: String, postgresUri: String, readonly: Boolean = false) {
        val (jdbcUrl, username, password) = parsePostgresUri(postgresUri)

        val manager = LocalDataSourceManager.getInstance(project)
        val existing = manager.dataSources.find { it.name == name }

        if (existing != null) {
            manager.removeDataSource(existing)
        }

        val dataSource = LocalDataSource()
        dataSource.name = name
        dataSource.url = jdbcUrl
        dataSource.username = username
        dataSource.passwordStorage = LocalDataSource.Storage.PERSIST
        dataSource.isReadOnly = readonly

        val driver = DatabaseDriverManager.getInstance().getDriver("postgresql")
        if (driver != null) {
            dataSource.setDatabaseDriver(driver)
        }

        DatabaseCredentials.getInstance().setPassword(dataSource, OneTimeString(password))
        dataSource.resolveDriver()
        dataSource.ensureDriverConfigured()

        manager.addDataSource(dataSource)
    }

    private fun parsePostgresUri(uri: String): Triple<String, String, String> {
        return if (uri.startsWith("jdbc:")) {
            // JDBC format: jdbc:postgresql://host:port/database?user=username&password=password
            Triple(uri, extractQueryParam(uri, "user") ?: "postgres", extractQueryParam(uri, "password") ?: "")
        } else if (uri.startsWith("postgres://") || uri.startsWith("postgresql://")) {
            // Standard format: postgres://username:password@host:port/database
            val standardUri = URI(uri)
            val userInfo = standardUri.userInfo?.split(":") ?: listOf("postgres", "")
            val username = userInfo.getOrNull(0) ?: "postgres"
            val password = userInfo.getOrNull(1) ?: ""
            val port = standardUri.port.takeIf { it > 0 } ?: 5432
            val jdbcUrl = "jdbc:postgresql://${standardUri.host}:$port${standardUri.path}"
            Triple(jdbcUrl, username, password)
        } else {
            // Assume JDBC format
            Triple(uri, "postgres", "")
        }
    }

    private fun extractQueryParam(uri: String, param: String): String? {
        val query = uri.substringAfterLast("?", "")
        if (query.isEmpty()) return null
        return query.split("&")
            .mapNotNull { pair ->
                val (key, value) = pair.split("=", limit = 2).let { it[0] to it.getOrNull(1) }
                if (key == param) value else null
            }
            .firstOrNull()
    }
}
