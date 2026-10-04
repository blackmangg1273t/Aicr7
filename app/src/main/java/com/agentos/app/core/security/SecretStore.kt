package com.agentos.app.core.security

/** Abstraction over secure secret storage (enables honest unit testing). */
interface SecretStore {
    fun getSecret(key: String): String?
    fun saveSecret(key: String, value: String)
    fun deleteSecret(key: String)
    fun wipeAll()
}
