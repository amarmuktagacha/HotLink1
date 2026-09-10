package com.shohan.hotlink.network

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue

/**
 * Runs a TCP server that accepts exactly one connected viewer at a time.
 * Handshake: viewer sends its pairing code, server replies 1 byte ack (1=ok, 0=rejected),
 * then (if accepted) 4 bytes video width + 4 bytes video height, then a continuous
 * stream of length-prefixed H.264 access units.
 */
class StreamServer(
    private val port: Int,
    private val pairingCode: String,
    private val videoWidth: Int,
    private val videoHeight: Int
) {

    var onClientConnected: (() -> Unit)? = null
    var onClientDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var writeThread: Thread? = null
    @Volatile private var clientSocket: Socket? = null
    private val frameQueue = LinkedBlockingQueue<ByteArray>(60)

    fun start() {
        running = true
        acceptThread = Thread {
            try {
                val server = ServerSocket(port)
                serverSocket = server
                while (running) {
                    val socket = try {
                        server.accept()
                    } catch (e: IOException) {
                        if (running) onError?.invoke(e.message ?: "সার্ভার ত্রুটি")
                        break
                    }
                    handleClient(socket)
                }
            } catch (e: IOException) {
                if (running) onError?.invoke(e.message ?: "পোর্ট চালু করা যায়নি")
            }
        }
        acceptThread?.start()
    }

    private fun handleClient(socket: Socket) {
        try {
            val input = DataInputStream(socket.getInputStream())
            val codeLen = input.readInt()
            if (codeLen !in 1..64) {
                socket.close()
                return
            }
            val codeBytes = ByteArray(codeLen)
            input.readFully(codeBytes)
            val receivedCode = String(codeBytes, Charsets.UTF_8)

            val output = DataOutputStream(socket.getOutputStream())
            if (receivedCode != pairingCode) {
                output.writeByte(0)
                output.flush()
                socket.close()
                return
            }
            output.writeByte(1)
            output.writeInt(videoWidth)
            output.writeInt(videoHeight)
            output.flush()

            // Replace any previously connected client with this one
            clientSocket?.let { old -> try { old.close() } catch (e: Exception) { } }
            clientSocket = socket
            frameQueue.clear()
            onClientConnected?.invoke()

            writeThread?.interrupt()
            val thread = Thread {
                try {
                    val out = DataOutputStream(socket.getOutputStream())
                    while (running && clientSocket === socket) {
                        val frame = frameQueue.take()
                        out.writeInt(frame.size)
                        out.write(frame)
                        out.flush()
                    }
                } catch (e: Exception) {
                    // client disconnected or queue interrupted, fall through to cleanup
                } finally {
                    if (clientSocket === socket) {
                        clientSocket = null
                        onClientDisconnected?.invoke()
                    }
                    try { socket.close() } catch (e: Exception) { }
                }
            }
            writeThread = thread
            thread.start()
        } catch (e: Exception) {
            try { socket.close() } catch (ignored: Exception) { }
        }
    }

    fun sendFrame(data: ByteArray) {
        if (!running) return
        if (frameQueue.remainingCapacity() == 0) {
            frameQueue.poll() // drop the oldest queued frame to keep latency low
        }
        frameQueue.offer(data)
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (e: Exception) { }
        try { clientSocket?.close() } catch (e: Exception) { }
        writeThread?.interrupt()
        acceptThread?.interrupt()
        frameQueue.clear()
        clientSocket = null
    }
}
