package io.atlantica.server

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

class MinecraftFrameDecoder : ByteToMessageDecoder() {

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, output: MutableList<Any>) {
        input.markReaderIndex()

        var length = 0
        var shift = 0
        while (shift < 35) {
            if (!input.isReadable) {
                input.resetReaderIndex()
                return
            }

            val byte = input.readByte().toInt()
            length = length or ((byte and 0x7F) shl shift)
            if ((byte and 0x80) == 0) {
                if (input.readableBytes() < length) {
                    input.resetReaderIndex()
                    return
                }

                output.add(input.readRetainedSlice(length))
                return
            }
            shift += 7
        }

        ctx.close()
    }
}
