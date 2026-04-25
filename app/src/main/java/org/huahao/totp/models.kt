package org.huahao.totp

import kotlinx.serialization.Serializable

@Serializable
data class AuthEntry(
    val id: String,
    val issuer: String,
    val account: String,
    val secret: String,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30
)