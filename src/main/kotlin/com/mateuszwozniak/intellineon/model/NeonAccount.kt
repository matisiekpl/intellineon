package com.mateuszwozniak.intellineon.model

data class NeonAccount(
    val id: String,
    val displayName: String,
    val email: String,
    val organizations: List<NeonOrganization> = emptyList(),
    val error: String? = null
)
