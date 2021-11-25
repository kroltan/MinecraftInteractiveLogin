package me.kroltan.interactivelogin

import fr.xephi.authme.api.v3.AuthMeApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level
import kotlin.concurrent.thread

data class AuthorizationMethodEntry(
    val name: String,
    val method: AuthorizationMethod,
    val config: ConfigurationSection
)

class InteractiveLoginPlugin : JavaPlugin(), Listener {
    lateinit var methods: List<AuthorizationMethodEntry>

    private val initialization = Job()

    private val authMe: AuthMeApi
        get() = AuthMeApi.getInstance()

    private val commandExecutor = ReactiveCommandExecutor()

    override fun onEnable() {
        saveDefaultConfig()

        methods = config.require<ConfigurationSection>("methods")
            .getValues(false)
            .map { it.value as ConfigurationSection }
            .mapNotNull {
                val name = it.name
                val className = it.require<String>("class")
                val clazz = Class.forName(className)

                if (!clazz.interfaces.contains(AuthorizationMethod::class.java)) {
                    logger.log(Level.WARNING, "")
                    return@mapNotNull null
                }

                val handler = clazz.getConstructor().newInstance() as AuthorizationMethod

                AuthorizationMethodEntry(name, handler, it)
            }
            .toList()

        thread {
            runBlocking {
                methods
                    .map {
                        async {
                            it.method.onEnable(this@InteractiveLoginPlugin, it.config)
                        }
                    }
                    .awaitAll()

                initialization.complete()
            }
        }

        getCommand("interactive-login")!!.setExecutor(commandExecutor)
        server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        thread {
            runBlocking {
                methods
                    .map { async { it.method.onDisable() } }
                    .awaitAll()
            }
        }
    }

    fun register(player: Player) {
        authMe.registerPlayer(player.name, randomString(16))
        authMe.forceLogin(player)
    }

    fun login(player: Player) {
        authMe.forceLogin(player)
    }

    fun onCommand(): Flow<CommandInvocation> {
        return commandExecutor
            .filter { it.label == "interactive-login" }
            .filter { it.args.isNotEmpty() }
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        thread {
            runBlocking {
                initialization.join()

                withContext(Cleanup()) {
                    try {
                        handleJoin(event)
                    } catch (e: Exception) {
                        currentCoroutineContext()[Cleanup.Key]!!.cleanup()

                        logger.log(Level.SEVERE, e.stackTraceToString())

                        event.player.sendMessage(component {
                            text(
                                config
                                    .text("error-message")
                                    .translate(
                                        "player" to event.player.name,
                                        "error-message" to (e.message ?: "No message"),
                                        "error-type" to e.javaClass.canonicalName
                                    )
                            )
                        })
                    }
                }
            }
        }
    }

    private suspend fun handleJoin(event: PlayerJoinEvent) {
        val step: JoinStep = when (authMe.isRegistered(event.player.name)) {
            true -> JoinStep.Login
            false -> JoinStep.Register
        }

        step.recurse {
            when (val result = run(this@InteractiveLoginPlugin, event.player)) {
                is JoinStep.Result.Done -> null
                is JoinStep.Result.Step -> result.step
            }
        }
    }
}
