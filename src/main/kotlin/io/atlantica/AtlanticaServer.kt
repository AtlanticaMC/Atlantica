package io.atlantica

import io.atlantica.config.Config
import io.atlantica.profiler.Profiler
import io.atlantica.server.NettyServer
import java.io.File
import java.nio.file.Files


class AtlanticaServer {
    val nettyServer = NettyServer(this)
    val config = Config()

    init {
        Profiler.section("Server") {
            //Init config
            if (Files.notExists(File(config.configPath).toPath())) {
                AtlanticaServer::class.java.getResourceAsStream("/config.toml").use { input ->
                    Files.copy(input, File(config.configPath).toPath())
                }
            }
        }
    }
    fun start() {
        Profiler.section("Server-Startup") {
            nettyServer.start()
        }
    }

}