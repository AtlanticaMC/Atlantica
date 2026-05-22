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
        return  CompletableFuture.supplyAsync {
            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel?) {
                        val chain = ch?.pipeline()
                        //TODO Add handlers to the pipeline
                    }
                })
            if (isAddressInUse("1", 1)) {
                log("Address is already in use, shutting down server", LogType.ERROR)
                exitProcess(0)
            }
            bootstrap.bind(InetSocketAddress(server.config.toml.getString("ip"),
                server.config.toml.getString("port")?.toInt() ?: 25565
            )).await()
            log("DockyardMC server running on IP:Port", LogType.SUCCESS)
            null
        }
    }

}