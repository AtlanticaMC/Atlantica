package io.atlantica.config

import kotlinx.io.files.FileSystem
import org.tomlj.Toml
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class Config {
    val configPath: String = "config.toml"
    val file = Files.readString(File(configPath).toPath())
    val toml = Toml.parse(file)
}