package com.example.foregroundservice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.runtime.mutableStateOf

class MyQSTileService : TileService() {

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var wifiDirectManager: WifiDirectManager
    private var shrareisOn= mutableStateOf(false)

    private var myBoundService: CounterService ?=null
    private var isBound=false
    private val connection =object : ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val localbinder=service as CounterService.LocalBinder
            myBoundService=localbinder.getService()
            isBound=true

        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound=false
            myBoundService=null
        }
    }
    private fun bindToService() {
        val intent = Intent(this, CounterService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }
    override fun onTileAdded() {
        super.onTileAdded()


        initializeWifiP2P()

        qsTile?.apply {
            label = "Cam Share"
            state= Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        bindToService()
        initializeWifiP2P()

        qsTile?.apply {
            label = "Cam Share"
             if (isBound && myBoundService?.shareMode() == true) {
                 state =Tile.STATE_ACTIVE
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        shrareisOn.value=!shrareisOn.value
        initializeWifiP2P()

        qsTile?.apply {
            if (state == Tile.STATE_ACTIVE && !shrareisOn.value) {
                state = Tile.STATE_INACTIVE

                wifiDirectManager.removeGroup()

                val intent = Intent(this@MyQSTileService, CounterService::class.java).apply {
                    action = "STOP_SHARE"
                }
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O){
                    startForegroundService(intent)
                }

            } else {
                state = Tile.STATE_ACTIVE

                wifiDirectManager.createWifiDirectGroup()

                val intent = Intent(this@MyQSTileService, CounterService::class.java).apply {
                    action = "SHARE"
                }
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O){
                    startForegroundService(intent)
                }
            }
            updateTile()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }

    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun initializeWifiP2P() {
        if (!::wifiP2pManager.isInitialized) {
            wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        }
        if (!::channel.isInitialized) {
            channel = wifiP2pManager.initialize(this, mainLooper, null)
        }
        if (!::wifiDirectManager.isInitialized) {
            wifiDirectManager = WifiDirectManager(this, wifiP2pManager, channel)
        }
    }
}
