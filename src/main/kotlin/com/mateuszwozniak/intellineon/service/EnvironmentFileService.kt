package com.mateuszwozniak.intellineon.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

@Service(Service.Level.PROJECT)
class EnvironmentFileService(private val project: Project) {

    private fun envFile(): File? = project.basePath?.let { File(it, ".env") }

    fun hasEnvFile(): Boolean = envFile()?.exists() == true

    fun hasKey(key: String): Boolean =
        envFile()?.readLines()?.any { it.startsWith("$key=") } == true

    fun unpooledUrlKey(): String =
        if (hasKey("DATABASE_URL_UNPOOLED")) "DATABASE_URL_UNPOOLED" else "DATABASE_URL_DIRECT"

    fun upsertEntries(entries: Map<String, String>) {
        val envFile = envFile() ?: error(".env file not found")
        if (!envFile.exists()) error(".env file does not exist")
        val lines = envFile.readLines().toMutableList()
        for ((key, value) in entries) {
            val index = lines.indexOfFirst { it.startsWith("$key=") }
            val line = "$key=\"$value\""
            if (index >= 0) {
                lines[index] = line
            } else {
                lines.add(line)
            }
        }
        envFile.writeText(lines.joinToString("\n") + "\n")
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(envFile)
    }
}
