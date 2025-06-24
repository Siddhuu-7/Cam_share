package com.example.foregroundservice

import android.app.Application
import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CentralDataStore(application: Application) : AndroidViewModel(application) {

    data class DEVICES(val device: WifiP2pDevice, val CONNECTION_STATUS: String)
    data class CONNECTIONDEVICE(val deviceAddress: String, val CONNECTION_STATUS: String)

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    private val _connection = MutableStateFlow(CONNECTIONDEVICE("", ""))

    val peers: StateFlow<List<WifiP2pDevice>> get() = _peers
    val connection: StateFlow<CONNECTIONDEVICE> get() = _connection

    val deviceList: StateFlow<List<DEVICES>> = combine(_peers, _connection) { peerList, connStatus ->
        peerList.map { device ->
            val status = if (device.deviceAddress == connStatus.deviceAddress)
                connStatus.CONNECTION_STATUS
            else
                "Available"
            DEVICES(device, status)
        }
}.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updatePeers(peerList: List<WifiP2pDevice>) {
        _peers.value = peerList
    }

    fun updateConnection(connectionDevice: CONNECTIONDEVICE) {
        _connection.value = connectionDevice
    }

}

