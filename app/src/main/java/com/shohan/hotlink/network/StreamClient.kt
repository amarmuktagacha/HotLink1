package com.shohan.hotlink.network

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Connects to a StreamServer, performs the pairing handshake, then continuously
 * reads length-prefixed H.264 access units and delivers them via onFrame.
 */
class StreamClient {

    var onConnected: ((width: Int, height: Int) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onFrame: ((ByteArray) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    private var readThread: Thread? = null

    fun connect(host: String, port: Int, pairingCode: String) {
        running = true
        val thread = Thread {
            var connectedSocket: Socket? = null
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 8000)
                connectedSocket = s
                socket = s

                val output = DataOutputStream(s.getOutputStream())
                val codeBytes = pairingCode.toByteArray(Charsets.UTF_8)
                output.writeInt(codeBytes.size)
                output.write(codeBytes)
                output.flush()

                val input = DataInputStream(s.getInputStream())
                val ack = input.readByte()
                if (ack.toInt() != 1) {
                    onError?.invoke("পেয়ারিং কোড মিলছে না")
                    running = false
                    return@Thread
                }

                val width = input.readInt()
                val height = input.readInt()
                onConnected?.invoke(width, height)

                while (running) {
                    val len = input.readInt()
                    if (len <= 0 || len > 5_000_000) break
                    val data = ByteArray(len)
                    input.readFully(data)
                    onFrame?.invoke(data)
                }
            } catch (e: IOException) {
                if (running) onError?.invoke(e.message ?: "সংযোগ করা যায়নি")
            } catch (e: Exception) {
                if (running) onError?.invoke(e.message ?: "অপ্রত্যাশিত ত্রুটি")
            } finally {
                val wasRunning = running
                running = false
                try { connectedSocket?.close() } catch (ignored: Exception) { }
                if (wasRunning) onDisconnected?.invoke()
            }
        }
        readThread = thread
        thread.start()
    }

    fun disconnect() {
        running = false
        try { socket?.close() } catch (e: Exception) { }
        readThread?.interrupt()
    }
}
