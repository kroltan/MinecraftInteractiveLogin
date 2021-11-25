package me.kroltan.interactivelogin

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.thread

data class CommandInvocation(
    val sender: CommandSender,
    val command: Command,
    val label: String,
    val args: List<String>
)

class ReactiveCommandExecutor : CommandExecutor, Flow<CommandInvocation> {
    private val inner = MutableSharedFlow<CommandInvocation>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        thread {
            runBlocking {
                inner.emit(CommandInvocation(sender, command, label, args.toList()))
            }
        }

        return true
    }

    @InternalCoroutinesApi
    override suspend fun collect(collector: FlowCollector<CommandInvocation>) {
        inner.collect(collector)
    }
}