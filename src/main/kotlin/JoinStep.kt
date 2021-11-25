package me.kroltan.interactivelogin

import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.entity.Player
import java.util.logging.Level

sealed class JoinStep {
    sealed class Result {
        data class Step(val step: JoinStep) : Result()
        class Done(val session: AuthorizationSession) : Result()
    }

    abstract suspend fun run(plugin: InteractiveLoginPlugin, player: Player): Result

    object Register : JoinStep() {
        override suspend fun run(plugin: InteractiveLoginPlugin, player: Player): Result {
            val translations = plugin.config.translations(
                "player" to player.name,
            )

            val registrationCandidates = plugin.methods
                .filter { it.method.eligibleForRegistration(player) }
                .toList()

            val selected = when (registrationCandidates.count()) {
                0 -> return Result.Step(Stuck(translations.of("no-methods-kick")))
                1 -> registrationCandidates.first()
                else -> {
                    player.sendMessage(
                        methodChoiceMessage(
                            plugin.config.translations(),
                            "registration-options-prefix",
                            registrationCandidates
                        )
                    )

                    plugin.onCommand()
                        .filter { it.sender == player }
                        .mapNotNull { it.args.joinToString(" ") }
                        .first()
                        .let { chosenName ->
                            registrationCandidates.first { it.name == chosenName }
                        }
                }
            }

            val session = AuthorizationSession.RegistrationSession(player, randomString(48))

            when (selected.method.register(session)) {
                RegistrationResult.Success -> {}
                RegistrationResult.Abort -> return Result.Step(Stuck(translations.of("login-aborted-kick")))
            }

            plugin.register(player)

            plugin.logger.log(Level.INFO, "Registered ${player.name} using ${selected.name}")

            return Result.Done(session)
        }
    }

    object Login : JoinStep() {
        override suspend fun run(plugin: InteractiveLoginPlugin, player: Player): Result {
            val translations = plugin.config.translations(
                "player" to player.name
            )

            val loginCandidates = plugin.methods
                .filter { it.method.isRegistered(player) }
                .toList()

            val selected = when (loginCandidates.count()) {
                0 -> return Result.Step(Register)
                1 -> loginCandidates.first()
                else -> {
                    player.sendMessage(
                        methodChoiceMessage(
                            translations,
                            "login-options-prefix",
                            loginCandidates
                        )
                    )

                    plugin.onCommand()
                        .filter { it.sender == player }
                        .mapNotNull { it.args.joinToString(" ") }
                        .first()
                        .let { chosenName ->
                            loginCandidates.first { it.name == chosenName }
                        }
                }
            }

            val session = AuthorizationSession.LoginSession(player, randomString(32))

            when (selected.method.login(session)) {
                LoginResult.Success -> {}
                LoginResult.Unauthorized -> {
                    val extended = translations + mapOf("method" to selected.name)
                    return Result.Step(Refuse(extended.of("unauthorized")))
                }
            }

            plugin.login(player)
            return Result.Done(session)
        }
    }

    data class Stuck(val reason: String) : JoinStep() {
        override suspend fun run(plugin: InteractiveLoginPlugin, player: Player): Result {
            return when (val policy = plugin.config.require<String>("policy.no-methods")) {
                "kick" -> Result.Step(Refuse(reason))
                "stay" -> Result.Step(Accept)
                else -> throw InvalidConfigurationException("Unknown policy $policy")
            }
        }
    }

    object Accept : JoinStep() {
        override suspend fun run(plugin: InteractiveLoginPlugin, player: Player): Result {
            plugin.logger.log(
                Level.INFO,
                "Player ${player.name} was not eligible for registration, but policy allows their stay."
            )

            return Result.Done(AuthorizationSession.Empty)
        }
    }

    data class Refuse(val reason: String) : JoinStep() {
        override suspend fun run(plugin: InteractiveLoginPlugin, player: Player): Result {
            player.kick(errorMessage(reason))

            return Result.Done(AuthorizationSession.Empty)
        }
    }
}
