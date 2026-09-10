package com.shohan.hotlink.decode

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import java.util.concurrent.LinkedBlockingQueue

/**
 * Feeds a raw H.264 elementary stream (as produced by ScreenCaptureService's encoder,
 * which prepends SPS/PPS to every IDR frame) into a MediaCodec decoder that renders
 * straight to the given Surface.
 */
class H264Decoder(
    private val surface: Surface,
    private val width: Int,
    private val height: Int
) {

    private var codec: MediaCodec? = null
    @Volatile private var running = false
    private val inputQueue = LinkedBlockingQueue<ByteArray>(60)
    private var feedThread: Thread? = null
    private var drainThread: Thread? = null

    fun start() {
        require(width > 0 && height > 0) { "Invalid video dimensions: ${width}x$height" }
        check(surface.isValid) { "Video surface is not valid" }
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            c.configure(format, surface, null, 0)
            c.start()
        } catch (error: Exception) {
            try { c.stop() } catch (_: Exception) { }
            try { c.release() } catch (_: Exception) { }
            throw error
        }
        codec = c
        running = true

        feedThread = Thread {
            while (running) {
                val data = try {
                    inputQueue.take()
                } catch (e: InterruptedException) {
                    break
                }
                val current = codec ?: break
                try {
                    val inIndex = current.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inputBuffer = current.getInputBuffer(inIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(data)
                        current.queueInputBuffer(inIndex, 0, data.size, System.nanoTime() / 1000, 0)
                    }
                } catch (e: Exception) {
                    if (running) break
                }
            }
        }
        feedThread?.start()

        drainThread = Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            while (running) {
                val current = codec ?: break
                try {
                    val outIndex = current.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outIndex >= 0) {
                        current.releaseOutputBuffer(outIndex, true)
                    }
                } catch (e: Exception) {
                    if (running) break
                }
            }
        }
        drainThread?.start()
    }

    fun feed(data: ByteArray) {
        if (!running) return
        if (inputQueue.remainingCapacity() == 0) {
            inputQueue.poll()
        }
        inputQueue.offer(data)
    }

    fun stop() {
        running = false
        feedThread?.interrupt()
        drainThread?.interrupt()
        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            // codec may already be in an invalid state, ignore
        }
        codec = null
        inputQueue.clear()
    }
}
