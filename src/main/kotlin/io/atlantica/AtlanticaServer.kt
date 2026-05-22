package io.atlantica

import io.atlantica.config.Config
import io.atlantica.profiler.Profiler
import io.atlantica.server.NettyServer


class AtlanticaServer {
    val nettyServer = NettyServer(this)
    val config = Config()

    init {
        Profiler.section("Server") {
            config
        }
    }
    fun start() {
        Profiler.section("Server-Startup") {
            nettyServer.start()
        }
    }

}
