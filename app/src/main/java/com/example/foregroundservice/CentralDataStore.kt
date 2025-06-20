package com.example.foregroundservice

import android.app.Application
import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CentralDataStore(application: Application) : AndroidViewModel(application) {


    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    private val _connection =MutableStateFlow<String>("")
    private val _isGroupCreated=MutableStateFlow<Boolean>(false)
    val peers: StateFlow<List<WifiP2pDevice>> get() = _peers
    val connection: StateFlow<String> get()=_connection
    val isGroupCreated: StateFlow<Boolean> get()=_isGroupCreated
    fun updatePeers(peerList: List<WifiP2pDevice>) {
        _peers.value = peerList
    }
    fun updateConnection(connection:String){
        _connection.value=connection
    }
    fun updateIsGroupCreation(status: Boolean){
        _isGroupCreated.value=status
    }
}

