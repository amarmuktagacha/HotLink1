package com.shohan.hotlink.capture

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.shohan.hotlink.R
import com.shohan.hotlink.network.Protocol
import com.shohan.hotlink.network.StreamServer
import com.shohan.hotlink.util.PairingCodeUtil

/**
 * Owns the complete host capture lifecycle.
 *
 * Important Android 14 rule: the user-approved MediaProjection token must be
 * obtained before starting the mediaProjection foreground-service type.
 */
class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.shohan.hotlink.action.START"
        const val ACTION_STOP = "com.shohan.hotlink.action.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_PAIRING_CODE = "extra_pairing_code"

        private const val TAG = "HotLinkCapture"
        private const val CHANNEL_ID = "hotlink_capture"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_EDGE = 1280

        var onClientConnected: (() -> Unit)? = null
        var onClientDisconnected: (() -> Unit)? = null
        var onError: ((String) -> Unit)? = null
    }

    private lateinit var projectionManager: MediaProjectionManager
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var codec: MediaCodec? = null
    private var server: StreamServer? = null
    private var encoderThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var stopping = false

    override fun onCreate() {
        super.onCreate()
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startFromIntent(intent)
            ACTION_STOP -> {
                stopCapture()
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startFromIntent(intent: Intent) {
        if (running || stopping) return

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        val pairingCode = intent.getStringExtra(EXTRA_PAIRING_CODE) ?: PairingCodeUtil.generate()

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            reportError("স্ক্রিন শেয়ারের অনুমতি পাওয়া যায়নি")
            stopSelf()
            return
        }

        try {
            // This must happen before startForeground(...MEDIA_PROJECTION) on Android 14+.
            val approvedProjection = projectionManager.getMediaProjection(resultCode, resultData)
                ?: throw IllegalStateException("MediaProjection তৈরি করা যায়নি")
            projection = approvedProjection
            approvedProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopCapture()
                    stopSelf()
                }
            }, null)

            startForegroundSafely()
            startCapture(approvedProjection, pairingCode)
        } catch (security: SecurityException) {
            Log.e(TAG, "Media projection was rejected", security)
            reportError("স্ক্রিন শেয়ার চালু করা যায়নি: অনুমতি বা সিস্টেম সেটিংস পরীক্ষা করুন")
            stopCapture()
            stopSelf()
        } catch (error: Exception) {
            Log.e(TAG, "Capture startup failed", error)
            reportError(error.message ?: "স্ক্রিন শেয়ার চালু করা যায়নি")
            stopCapture()
            stopSelf()
        }
    }

    private fun startForegroundSafely() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle(getString(R.string.notif_sharing_title))
            .setContentText(getString(R.string.notif_sharing_text))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun startCapture(activeProjection: MediaProjection, pairingCode: String) {
        val size = displaySize()
        var width = size.first
        var height = size.second
        val longEdge = maxOf(width, height)
        if (longEdge > MAX_EDGE) {
            val scale = MAX_EDGE.toFloat() / longEdge
            width = (width * scale).toInt()
            height = (height * scale).toInt()
        }
        width = width and 1.inv()
        height = height and 1.inv()
        require(width >= 2 && height >= 2) { "ডিসপ্লের আকার সঠিক নয়" }

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val newCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try {
            newCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = newCodec.createInputSurface()
            newCodec.start()
            codec = newCodec

            display = activeProjection.createVirtualDisplay(
                "HotLinkCapture", width, height, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, null
            ) ?: throw IllegalStateException("Virtual display তৈরি করা যায়নি")

            val newServer = StreamServer(Protocol.DEFAULT_PORT, pairingCode, width, height)
            newServer.onClientConnected = { onClientConnected?.invoke() }
            newServer.onClientDisconnected = { onClientDisconnected?.invoke() }
            newServer.onError = { reportError(it) }
            newServer.start()
            server = newServer

            running = true
            encoderThread = Thread(::encodeLoop, "HotLinkEncoder").also { it.start() }
        } catch (error: Exception) {
            try { newCodec.stop() } catch (_: Exception) { }
            try { newCodec.release() } catch (_: Exception) { }
            throw error
        }
    }

    private fun displaySize(): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun encodeLoop() {
        val info = MediaCodec.BufferInfo()
        while (running && !Thread.currentThread().isInterrupted) {
            val current = codec ?: break
            try {
                val index = current.dequeueOutputBuffer(info, 10_000)
                if (index >= 0) {
                    current.getOutputBuffer(index)?.let { buffer ->
                        if (info.size > 0) {
                            val bytes = ByteArray(info.size)
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            buffer.get(bytes)
                            server?.sendFrame(bytes)
                        }
                    }
                    current.releaseOutputBuffer(index, false)
                }
            } catch (error: Exception) {
                if (running) Log.e(TAG, "Encoder loop stopped", error)
                break
            }
        }
    }

    private fun stopCapture() {
        if (stopping) return
        stopping = true
        running = false
        val thread = encoderThread
        if (Thread.currentThread() !== thread) {
            try { thread?.join(500) } catch (_: Exception) { }
        }
        encoderThread = null
        try { display?.release() } catch (_: Exception) { }
        display = null
        try { codec?.stop() } catch (_: Exception) { }
        try { codec?.release() } catch (_: Exception) { }
        codec = null
        try { server?.stop() } catch (_: Exception) { }
        server = null
        try { projection?.stop() } catch (_: Exception) { }
        projection = null
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) { }
        stopping = false
    }

    private fun reportError(message: String) {
        try { onError?.invoke(message) } catch (_: Exception) { }
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
