package io.atlantica.server

import cz.lukynka.prettylog.GlobalPrettyLogger.log
import cz.lukynka.prettylog.LogType
import io.atlantica.AtlanticaServer
import io.atlantica.utils.isAddressInUse
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

class NettyServer(val server: AtlanticaServer) {

    val bossGroup = NioEventLoopGroup()
    val workerGroup = NioEventLoopGroup()

    fun start(): CompletableFuture<Void> {
        return CompletableFuture.runAsync {
            val host = server.config.toml.getString("ip") ?: "0.0.0.0"
            val port = server.config.toml.getLong("port")?.toInt() ?: 25565
            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline()
                            .addLast(MinecraftFrameDecoder())
                            .addLast(MinecraftStatusHandler(server))
                    }
                })
            if (isAddressInUse(host, port)) {
                log("Address is already in use, shutting down server", LogType.ERROR)
                exitProcess(0)
            }
            bootstrap.bind(InetSocketAddress(host, port)).sync()
            log("AtlanticaMC server running on $host:$port", LogType.SUCCESS)
        }
    }

}
