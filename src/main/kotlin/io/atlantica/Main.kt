package io.atlantica

import io.netty.util.internal.logging.InternalLoggerFactory
import io.netty.util.internal.logging.JdkLoggerFactory

fun main() {
    InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE)
    AtlanticaServer().start().join()
}
