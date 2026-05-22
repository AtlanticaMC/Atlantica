package io.atlantica.utils

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException

fun isAddressInUse(host: String, port: Int): Boolean {
    try {
        val socket = ServerSocket()
        socket.bind(InetSocketAddress(host, port))
        socket.close()
    } catch (exception: SocketException) {
        val message = exception.message ?: ""
        if (message.contains("Address already in use")) {
            return true;
        }
    }

    return false
}