package me.kroltan.interactivelogin

import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration


internal fun errorMessage(error: String) = component {
    text(error) styled {
        color(NamedTextColor.RED)
    }
}.asComponent()

internal fun methodChoiceMessage(
    translations: TranslationContext,
    prefix: String,
    candidates: Iterable<AuthorizationMethodEntry>
) = component {
    text(translations.of(prefix))
    text("\n")

    text(translations.of("choice-instructions"))
    text("\n")

    candidates.forEach {
        text(" - ")

        link(
            it.name,
            translations.of("candidate-tooltip"),
            ClickEvent.runCommand("/interactive-login ${it.name}")
        )

        text("\n")
    }
}.asComponent()

fun registrationCodeMessage(
    registrationTranslation: TranslationContext,
    registrationCode: String
) = component {
    text(registrationTranslation.of("registration-instructions")) styled {
        decoration(TextDecoration.BOLD, true)
    }
    text("\n")

    link(
        registrationCode,
        registrationTranslation.of("copy-to-clipboard"),
        ClickEvent.copyToClipboard(registrationCode)
    )
}.asComponent()
