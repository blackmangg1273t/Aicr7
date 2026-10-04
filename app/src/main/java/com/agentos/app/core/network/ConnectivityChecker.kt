package com.agentos.app.core.network

/** Abstraction over connectivity state. */
interface ConnectivityChecker {
    fun isOnline(): Boolean
}
