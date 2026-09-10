package com.shohan.hotlink.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.shohan.hotlink.capture.ScreenCaptureService
import com.shohan.hotlink.util.NetworkUtils
import com.shohan.hotlink.util.PairingCodeUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HostStatus { IDLE, WAITING_PERMISSION, SHARING, CLIENT_CONNECTED, ERROR }

data class HostUiState(
    val pairingCode: String = PairingCodeUtil.generate(),
    val status: HostStatus = HostStatus.IDLE,
    val localIps: List<String> = emptyList(),
    val errorMessage: String? = null
)

class HostViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HostUiState(localIps = NetworkUtils.getLocalIpAddresses()))
    val uiState: StateFlow<HostUiState> = _uiState.asStateFlow()

    init {
        ScreenCaptureService.onClientConnected = {
            _uiState.value = _uiState.value.copy(status = HostStatus.CLIENT_CONNECTED)
        }
        ScreenCaptureService.onClientDisconnected = {
            if (_uiState.value.status == HostStatus.CLIENT_CONNECTED) {
                _uiState.value = _uiState.value.copy(status = HostStatus.SHARING)
            }
        }
        ScreenCaptureService.onError = { message ->
            _uiState.value = _uiState.value.copy(status = HostStatus.ERROR, errorMessage = message)
        }
    }

    fun setWaitingPermission() {
        _uiState.value = _uiState.value.copy(status = HostStatus.WAITING_PERMISSION)
    }

    fun onSharingStarted() {
        _uiState.value = _uiState.value.copy(
            status = HostStatus.SHARING,
            localIps = NetworkUtils.getLocalIpAddresses()
        )
    }

    fun onStartFailed(message: String) {
        _uiState.value = _uiState.value.copy(
            status = HostStatus.ERROR,
            errorMessage = message
        )
    }

    fun onSharingStopped() {
        _uiState.value = _uiState.value.copy(status = HostStatus.IDLE, pairingCode = PairingCodeUtil.generate())
    }

    override fun onCleared() {
        super.onCleared()
        ScreenCaptureService.onClientConnected = null
        ScreenCaptureService.onClientDisconnected = null
        ScreenCaptureService.onError = null
    }
}
