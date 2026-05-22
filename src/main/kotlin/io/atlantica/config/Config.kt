package io.atlantica.config

import org.tomlj.Toml
import java.io.File
import java.nio.file.Files

class Config {
    val configPath: String = "config.toml"
    val file: String
    val toml: org.tomlj.TomlParseResult

    init {
        val path = File(configPath).toPath()
        if (Files.notExists(path)) {
            Config::class.java.getResourceAsStream("/config.toml").use { input ->
                requireNotNull(input) { "Default config.toml resource is missing" }
                Files.copy(input, path)
            }
        }

        file = Files.readString(path)
        toml = Toml.parse(file)
    }
}
