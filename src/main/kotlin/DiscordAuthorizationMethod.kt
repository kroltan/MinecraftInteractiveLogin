package me.kroltan.interactivelogin

import club.minnced.jda.reactor.ReactiveEventManager
import club.minnced.jda.reactor.on
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.awaitFirst
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.Button
import net.dv8tion.jda.api.interactions.components.ButtonStyle
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import reactor.core.Disposables
import java.lang.Exception
import java.util.logging.Level
import java.util.logging.Logger

object JDAClientCache {
    private val cache = mutableMapOf<String, JDA>()

    fun get(token: String): JDA {
        return cache.getOrPut(token) {
            JDABuilder
                .createLight(token)
                .setEventManager(ReactiveEventManager())
                .build()
        }
    }
}

@Suppress("unused")
class DiscordAuthorizationMethod : AuthorizationMethod {
    private lateinit var discord: JDA
    private lateinit var guild: Guild
    private lateinit var eligibleRoles: Set<Role>
    private lateinit var discordIdKey: NamespacedKey
    private lateinit var config: ConfigurationSection
    private lateinit var logger: Logger
    private val subscriptions = Disposables.composite()

    override suspend fun onEnable(plugin: Plugin, config: ConfigurationSection) {
        discordIdKey = NamespacedKey(plugin, "discordId")
        logger = plugin.logger
        this.config = config

        discord = JDAClientCache.get(config.require("token"))
            .on<ReadyEvent>()
            .awaitFirst()
            .jda

        guild = discord.getGuildById(config.require<Long>("guild"))!!
        eligibleRoles = config.require<List<Long>>("roles")
            .mapNotNull { guild.getRoleById(it) }
            .toSet()
    }

    override suspend fun onDisable() {
        subscriptions.dispose()
    }

    override suspend fun eligibleForRegistration(player: Player): Boolean = true

    override suspend fun isRegistered(player: Player): Boolean {
        val userId = player.persistentDataContainer
            .get(discordIdKey, PersistentDataType.LONG)
            ?: return false

        val member = guild.retrieveMemberById(userId)
            .submit()
            .await()

        return isEligible(member)
    }

    override suspend fun login(session: AuthorizationSession.LoginSession): LoginResult {
        val userId = session.player.persistentDataContainer
            .get(discordIdKey, PersistentDataType.LONG)!!

        val member = guild.retrieveMemberById(userId)
            .submit()
            .await()

        val loginTranslation = config.translations(
            "guild.name" to guild.name,
            "player" to session.player.name,
        )

        if (!isEligible(member)) {
            return LoginResult.Unauthorized
        }

        val channel = member.user
            .openPrivateChannel()
            .submit()
            .await()

        val message = channel
            .sendMessage(
                MessageBuilder()
                    .setContent(loginTranslation.of("login-message"))
                    .setActionRows(
                        ActionRow.of(
                            Button.of(
                                ButtonStyle.PRIMARY,
                                session.token,
                                loginTranslation.of("login-authorize-button")
                            )
                        )
                    )
                    .build()
            )
            .submit()
            .await()
            .withCleanup { delete().queue() }

        val clickEvent = config.withTimeout("policy.authorization-timeout") {
            discord.on<ButtonClickEvent>()
                .filter { it.message == message }
                .filter { it.button?.id == session.token }
                .awaitFirst()
        }

        clickEvent
            .editMessage(
                MessageBuilder()
                    .setContent(loginTranslation.of("login-confirmation-message"))
                    .build()
            )
            .submit()
            .await()
            .withCleanup { deleteOriginal().queue() }

        return LoginResult.Success
    }

    private fun isEligible(member: Member) = member.roles.intersect(eligibleRoles).any()

    override suspend fun register(session: AuthorizationSession.RegistrationSession): RegistrationResult {
        val translations = config.translations(
            "bot.name" to guild.selfMember.effectiveName,
            "guild.name" to guild.name,
            "player" to session.player.name
        )

        session.player.persistentDataContainer.remove(discordIdKey)

        session.player.sendMessage(
            registrationCodeMessage(
                translations,
                session.code
            )
        )

        val message = discord.on<MessageReceivedEvent>()
            .filter { it.channelType == ChannelType.PRIVATE }
            .filter { it.message.contentRaw.contains(session.code) }
            .awaitFirst()
            .message

        session.player.persistentDataContainer.set(discordIdKey, PersistentDataType.LONG, message.author.idLong)

        message
            .reply(translations.of("registration-confirmation"))
            .submit()
            .await()
            .withCleanup { delete().queue() }

        return RegistrationResult.Success
    }
}