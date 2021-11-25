package me.kroltan.interactivelogin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.time.withTimeout
import org.bukkit.configuration.ConfigurationSection
import java.time.Duration
import java.util.*
import kotlin.random.Random

inline fun <reified T> ConfigurationSection.require(key: String): T =
    get(key) as T ?: throw NullPointerException("Missing configuration value $key, expected ${T::class}")

fun ConfigurationSection.text(key: String): String =
    require("texts.$key")

fun ConfigurationSection.translations(vararg context: Pair<String, String>) = TranslationContext(this, mapOf(*context))

fun String.translate(values: Map<String, String>): String {
    if (values.isEmpty()) {
        return this
    }

    return Regex("""\$\[(.*?)]""")
        .replace(this) { values.getOrDefault(it.groups[1]!!.value, it.value) }
}

fun String.translate(vararg values: Pair<String, String>): String = translate(mapOf(*values))

data class TranslationContext(
    private val config: ConfigurationSection,
    private val context: Map<String, String>
) {
    fun of(key: String): String = fallback(config, "texts.$key")
        .translate(context)

    operator fun plus(extra: Map<String, String>): TranslationContext =
        TranslationContext(config, context + extra)

    private fun fallback(section: ConfigurationSection, key: String): String {
        val parent = section.parent ?: return section.require(key)

        return when (val raw = section.getString(key)) {
            null -> fallback(parent, key)
            else -> raw
        }
    }
}

suspend fun <T> ConfigurationSection.withTimeout(key: String, body: suspend CoroutineScope.() -> T): T =
    when (val raw = getLong(key)) {
        0L -> coroutineScope(body)
        else -> withTimeout(Duration.ofSeconds(raw), body)
    }

fun <T, I> T.chaining(items: Iterable<I>, block: T.(I) -> T): T =
    items.fold(this) { t, x -> t.block(x) }


suspend fun <T> T.recurse(block: suspend T.() -> T?): T {
    return when (val next = this.block()) {
        null -> this
        else -> next.recurse(block)
    }
}

fun randomString(length: Int): String = Base64.getEncoder().encodeToString(Random.nextBytes(length * 3 / 4))