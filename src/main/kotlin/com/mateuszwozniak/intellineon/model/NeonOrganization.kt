package com.mateuszwozniak.intellineon.model

data class NeonOrganization(
    val id: String,
    val name: String,
    val projects: List<NeonProject> = emptyList()
)
