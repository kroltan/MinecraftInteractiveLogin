package me.kroltan.interactivelogin

import net.kyori.adventure.text.*
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

data class ComponentDSL(
    private val content: String
) : ComponentLike {
    private var style: Style = Style.empty()
    private var children: MutableList<ComponentDSL> = mutableListOf()

    override fun asComponent(): Component {
        return Component.text()
            .style(style)
            .content(content)
            .chaining(children) { append(it) }
            .build()
    }

    fun text(value: String): ComponentDSL {
        val dsl = ComponentDSL(value)

        children.add(dsl)

        return dsl
    }

    infix fun ComponentDSL.styled(style: StyleDSL.() -> Unit) {
        val dsl = StyleDSL(Style.style())
        dsl.style()

        this.style = dsl.build()
    }
}

data class StyleDSL(
    private var builder: Style.Builder
) {
    fun build(): Style = builder.build()

    fun onClick(event: ClickEvent) {
        builder = builder.clickEvent(event)
    }

    fun onHover(event: HoverEvent<*>) {
        builder = builder.hoverEvent(event)
    }

    fun color(color: TextColor) {
        builder = builder.color(color)
    }

    fun decoration(decoration: TextDecoration, state: Boolean) {
        builder = builder.decoration(
            decoration, when (state) {
                true -> TextDecoration.State.TRUE
                false -> TextDecoration.State.FALSE
            }
        )
    }
}

fun component(body: ComponentDSL.() -> Unit): ComponentDSL {
    val dsl = ComponentDSL("")
    dsl.body()
    return dsl
}

/*
 * Opinoated components
 * */

fun ComponentDSL.link(label: String, tooltip: String?, event: ClickEvent) = text("[$label]") styled {
    color(NamedTextColor.GREEN)

    if (tooltip != null) {
        onHover(HoverEvent.showText(component {
            text(tooltip)
        }))
    }

    onClick(event)
}