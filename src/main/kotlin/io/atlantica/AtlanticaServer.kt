package io.atlantica

import io.atlantica.config.Config
import io.atlantica.profiler.Profiler
import io.atlantica.server.NettyServer
import java.util.concurrent.CompletableFuture


class AtlanticaServer {
    val nettyServer = NettyServer(this)
    val config = Config()

    init {
        Profiler.section("Server") {
            config
        }
    }
    fun start(): CompletableFuture<Void> {
        return Profiler.section("Server-Startup") {
            nettyServer.start()
        }
    }

}
