package io.atlantica.server

import io.atlantica.AtlanticaServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import java.nio.charset.StandardCharsets

class MinecraftStatusHandler(private val server: AtlanticaServer) : SimpleChannelInboundHandler<ByteBuf>() {

    private var protocolVersion = server.config.toml.getLong("protocol-version")?.toInt() ?: 772
    private var statusState = false

    override fun channelRead0(ctx: ChannelHandlerContext, packet: ByteBuf) {
        val packetId = packet.readVarInt()
        when {
            packetId == 0 && !statusState -> handleHandshake(ctx, packet)
            packetId == 0 && statusState -> sendStatus(ctx)
            packetId == 1 && statusState -> sendPong(ctx, packet)
            else -> ctx.close()
        }
    }

    private fun handleHandshake(ctx: ChannelHandlerContext, packet: ByteBuf) {
        protocolVersion = packet.readVarInt()
        packet.readString()
        packet.readUnsignedShort()
        val nextState = packet.readVarInt()

        if (nextState == 1) {
            statusState = true
        } else {
            ctx.close()
        }
    }

    private fun sendStatus(ctx: ChannelHandlerContext) {
        val maxPlayers = server.config.toml.getLong("max-players") ?: 100
        val onlinePlayers = server.config.toml.getLong("online-players") ?: 0
        val motd = server.config.toml.getString("motd") ?: "AtlanticaMC"
        val versionName = server.config.toml.getString("version-name") ?: "1.21.8"
        val response = """
            {"version":{"name":"${versionName.escapeJson()}","protocol":$protocolVersion},"players":{"max":$maxPlayers,"online":$onlinePlayers},"description":{"text":"${motd.escapeJson()}"}}
        """.trimIndent()

        ctx.writeAndFlush(createPacket(0) { it.writeString(response) })
    }

    private fun sendPong(ctx: ChannelHandlerContext, packet: ByteBuf) {
        val payload = packet.readLong()
        ctx.writeAndFlush(createPacket(1) { it.writeLong(payload) })
    }

    private fun createPacket(packetId: Int, writeBody: (ByteBuf) -> Unit): ByteBuf {
        val body = Unpooled.buffer()
        body.writeVarInt(packetId)
        writeBody(body)

        val frame = Unpooled.buffer()
        frame.writeVarInt(body.readableBytes())
        frame.writeBytes(body)
        body.release()
        return frame
    }

    private fun ByteBuf.readVarInt(): Int {
        var value = 0
        var position = 0
        while (position < 35) {
            val currentByte = readByte().toInt()
            value = value or ((currentByte and 0x7F) shl position)
            if ((currentByte and 0x80) == 0) return value
            position += 7
        }
        throw IllegalArgumentException("VarInt is too big")
    }

    private fun ByteBuf.writeVarInt(value: Int) {
        var remaining = value
        do {
            var temp = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining != 0) temp = temp or 0x80
            writeByte(temp)
        } while (remaining != 0)
    }

    private fun ByteBuf.readString(): String {
        val length = readVarInt()
        val bytes = ByteArray(length)
        readBytes(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun ByteBuf.writeString(value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeVarInt(bytes.size)
        writeBytes(bytes)
    }

    private fun String.escapeJson(): String = buildString {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}
