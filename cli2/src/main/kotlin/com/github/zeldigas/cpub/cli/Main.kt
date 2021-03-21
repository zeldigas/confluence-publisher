package com.github.zeldigas.cpub.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.sources.PropertiesValueSource
import com.github.ajalt.clikt.sources.ValueSource

class ConfluencePublisher : CliktCommand() {

    init {
        context {
            valueSources(
                    PropertiesValueSource.from(System.getProperties(), ValueSource.getKey(prefix = "cpub.", joinSubcommands = "."))
            )
        }
    }

    override fun run() = Unit

}

fun main(args:Array<String>) {
    ConfluencePublisher().subcommands(Upload())
            .main(args)
}