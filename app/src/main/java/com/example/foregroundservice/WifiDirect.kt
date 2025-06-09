package com.example.foregroundservice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.Manifest
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper

import android.widget.Toast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


class WifiDirectManager(
    private val context: Context,
    private val wifiP2pManager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel
) {
    val peers: StateFlow<List<WifiP2pDevice>> get() = _peers
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)

        // Indicates a change in the list of available peers.
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)

        // Indicates the state of Wi-Fi Direct connectivity has changed.
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)

        // Indicates this device's details have changed.
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }
    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())

    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val refreshedPeers = peerList.deviceList.toList()
        _peers.value = refreshedPeers
        Toast.makeText(context, "Discovered ${refreshedPeers.size} peers", Toast.LENGTH_SHORT).show()
    }
    private var isEnabled: Boolean=false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action){
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED

                    if (isEnabled)
                        Toast.makeText(
                            context,
                            "Wi-Fi Direct enabled: $isEnabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    else Toast.makeText(
                        context,
                        "Please enable Wi-Fi and Location Services",
                        Toast.LENGTH_LONG
                    ).show()

                }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        val hasPermission =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                context?.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
                            } else {
                                context?.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            }

                        if (hasPermission == true) {
                            wifiP2pManager.requestPeers(channel, peerListListener)
                        } else {
                            Toast.makeText(
                                context,
                                "Permission denied to access peers",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
        }
        }

    fun discoverPeers() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            Toast.makeText(context, "Missing permission for discovery", Toast.LENGTH_SHORT).show()
            return
        }

        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(context, "Discovery started", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(reason: Int) {
                when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> {
                        Toast.makeText(context, "Wi-Fi Direct not supported on this device", Toast.LENGTH_SHORT).show()
                    }
                    WifiP2pManager.BUSY -> {
                        Toast.makeText(context, "Wi-Fi Direct is busy, please try again later", Toast.LENGTH_SHORT).show()
                    }
                    WifiP2pManager.ERROR -> {
                        Toast.makeText(context, "An internal error occurred", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(context, "Discovery failed: $reason", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })


    }



    fun registerReceiver() {
        context.registerReceiver(receiver, intentFilter)
    }

    fun unregisterReceiver() {
        context.unregisterReceiver(receiver)
    }
}
