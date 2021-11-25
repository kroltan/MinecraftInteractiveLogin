package me.kroltan.interactivelogin

import kotlinx.coroutines.*
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

interface AuthorizationMethod {
    suspend fun onEnable(plugin: Plugin, config: ConfigurationSection)
    suspend fun onDisable()
    suspend fun eligibleForRegistration(player: Player): Boolean
    suspend fun isRegistered(player: Player): Boolean
    suspend fun login(session: AuthorizationSession.LoginSession): LoginResult
    suspend fun register(session: AuthorizationSession.RegistrationSession): RegistrationResult
}

sealed class LoginResult {
    object Success : LoginResult()
    object Unauthorized : LoginResult()
}

sealed class RegistrationResult {
    object Success : RegistrationResult()
    object Abort : RegistrationResult()
}

sealed class AuthorizationSession {
    object Empty : AuthorizationSession()
    data class LoginSession(val player: Player, val token: String) : AuthorizationSession()
    data class RegistrationSession(val player: Player, val code: String) : AuthorizationSession()
}


class Cleanup: CoroutineContext.Element {
    object Key : CoroutineContext.Key<Cleanup>

    private val pending = mutableListOf<suspend () -> Unit>()

    override val key: CoroutineContext.Key<*>
        get() = Key

    fun add(item: suspend () -> Unit) {
        pending.add(item)
    }

    suspend fun cleanup() {
        coroutineScope {
            pending
                .map { async { it() } }
                .awaitAll()

            pending.clear()
        }
    }
}

suspend fun <T> T.withCleanup(block: suspend T.() -> Unit): T {
    coroutineScope {
        currentCoroutineContext()[Cleanup.Key]!!.add {
            this@withCleanup.block()
        }
    }

    return this
}