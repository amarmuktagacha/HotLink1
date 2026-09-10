package com.shohan.hotlink.viewmodel

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import com.shohan.hotlink.decode.H264Decoder
import com.shohan.hotlink.network.Protocol
import com.shohan.hotlink.network.StreamClient
import com.shohan.hotlink.util.PairingCodeUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ViewerStatus { IDLE, CONNECTING, CONNECTED, ERROR }

data class ViewerUiState(
    val hostAddress: String = "",
    val pairingCode: String = "",
    val status: ViewerStatus = ViewerStatus.IDLE,
    val errorMessage: String? = null
)

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private val client = StreamClient()
    private var decoder: H264Decoder? = null
    private var pendingSurface: Surface? = null
    private var videoWidth = 0
    private var videoHeight = 0

    fun updateHostAddress(value: String) {
        _uiState.value = _uiState.value.copy(hostAddress = value)
    }

    fun updatePairingCode(value: String) {
        _uiState.value = _uiState.value.copy(pairingCode = PairingCodeUtil.normalize(value))
    }

    fun connect() {
        val state = _uiState.value
        if (state.hostAddress.isBlank() || !PairingCodeUtil.isValid(state.pairingCode)) {
            _uiState.value = state.copy(status = ViewerStatus.ERROR, errorMessage = "হোস্টের IP ও পেয়ারিং কোড দিন")
            return
        }
        _uiState.value = state.copy(status = ViewerStatus.CONNECTING, errorMessage = null)

        client.onConnected = { w, h ->
            videoWidth = w
            videoHeight = h
            _uiState.value = _uiState.value.copy(status = ViewerStatus.CONNECTED)
            tryStartDecoder()
        }
        client.onDisconnected = {
            decoder?.stop()
            decoder = null
            if (_uiState.value.status != ViewerStatus.IDLE) {
                _uiState.value = _uiState.value.copy(status = ViewerStatus.ERROR, errorMessage = "সংযোগ বিচ্ছিন্ন হয়েছে")
            }
        }
        client.onError = { message ->
            _uiState.value = _uiState.value.copy(status = ViewerStatus.ERROR, errorMessage = message)
        }
        client.onFrame = { data ->
            decoder?.feed(data)
        }

        client.connect(state.hostAddress.trim(), Protocol.DEFAULT_PORT, state.pairingCode)
    }

    fun onSurfaceReady(surface: Surface) {
        pendingSurface = surface
        tryStartDecoder()
    }

    fun onSurfaceDestroyed() {
        decoder?.stop()
        decoder = null
        pendingSurface = null
    }

    private fun tryStartDecoder() {
        val surface = pendingSurface ?: return
        if (videoWidth == 0 || videoHeight == 0) return
        if (decoder != null) return
        try {
            decoder = H264Decoder(surface, videoWidth, videoHeight).also { it.start() }
        } catch (error: Exception) {
            decoder = null
            _uiState.value = _uiState.value.copy(
                status = ViewerStatus.ERROR,
                errorMessage = error.message ?: "ভিডিও ডিকোডার চালু করা যায়নি"
            )
        }
    }

    fun disconnect() {
        client.disconnect()
        decoder?.stop()
        decoder = null
        _uiState.value = _uiState.value.copy(status = ViewerStatus.IDLE)
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
