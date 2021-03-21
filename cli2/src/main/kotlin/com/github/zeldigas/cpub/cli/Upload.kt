package com.github.zeldigas.cpub.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.long
import java.io.File
import java.net.URL
import java.nio.file.Path

class Upload : CliktCommand(name="upload", help="Converts asciidoc files and uploads them to confluence") {

    val confluenceUrl: URL by option("--confluence-url", envvar = "CONFLUENCE_URL").convert { URL(it) }.required()
    val convfluenceUser: String by option("--user", envvar = "CONFLUENCE_USER").required()
    val confluencePassword: String by option("--password", envvar = "CONFLUENCE_PASSWORD")
            .prompt(requireConfirmation = true, hideInput = true)
    val skipSsl:Boolean by option("--skip-ssl-verification")
            .flag("--no-skip-ssl-verification", default = false)

    val spaceKey: String? by option("--space", envvar = "CONFLUENCE_SPACE")
    val parentId: Long? by option("--parent-id").long()
    val removeOrphans: Boolean by option("--remove-orphans").flag("--keep-orphans", default = false)

    val docs: File by option("--docs").file(canBeFile = true, canBeDir = true).required()

    override fun run() {
        println(confluenceUrl)
    }
}