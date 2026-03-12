package com.mateuszwozniak.intellineon.model

data class NeonProject(
    val id: String,
    val name: String,
    val organizationId: String?,
    val branches: List<NeonBranch> = emptyList()
)
