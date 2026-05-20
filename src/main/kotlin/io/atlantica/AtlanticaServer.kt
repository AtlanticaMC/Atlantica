package io.atlantica.io.atlantica

import io.atlantica.io.atlantica.server.NettyServer

class AtlanticaServer {
    val nettyServer = NettyServer(this)
}