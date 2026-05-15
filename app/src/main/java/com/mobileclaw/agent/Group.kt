package com.mobileclaw.agent

data class Group(
    val id: String,
    val name: String,
    val emoji: String = "group",
    val memberRoleIds: List<String>,   // ordered; "user" is always implicitly present
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
