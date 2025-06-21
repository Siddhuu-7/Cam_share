package com.example.foregroundservice

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow



class WifiDirectManager(
    private val context: Context,
    private val wifiP2pManager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val centralDataStore: CentralDataStore? = null,
    ipAddress :((String?)->Unit)={}


) {
    enum class ConnectionSate{
        Connecting,
        Connected,
        Failed,
        Disconnected
    }


    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataExchange= DataExchange(serviceScope,context)
    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> get() = _peers

    private var isEnabled = false

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val refreshedPeers = peerList.deviceList.toList()
        _peers.value = refreshedPeers

    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED

                    if (isEnabled) {

                        Log.d("WifiP2p", "WiFi Direct is ON.")
                    } else {

                        Log.d("WifiP2p", "WiFi Direct is OFF.")
                    }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context?.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
                    } else {
                        context?.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    }

                    if (hasPermission == true) {
                        wifiP2pManager.requestPeers(channel, peerListListener)
                        Log.d("WifiP2p", "Requested peers.")
                    } else {
                        Toast.makeText(context, "Permission denied to access peers.", Toast.LENGTH_SHORT).show()
                        Log.d("WifiP2p", "Permission denied to access peers.")
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {

                    val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, android.net.NetworkInfo::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO) as? android.net.NetworkInfo
                    }
                    if (networkInfo?.isConnected == true) {
                        wifiP2pManager.requestConnectionInfo(
                            channel,
                            object : WifiP2pManager.ConnectionInfoListener {
                                override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
                                    if (info == null) {
                                        centralDataStore?.updateConnection(ConnectionSate.Disconnected.toString())
                                        return

                                    }

                                    if (info.groupFormed) {

                                        if (info.isGroupOwner) {
                                        } else {
                                            val serverIp = info.groupOwnerAddress.hostAddress
                                            Toast.makeText(
                                                context,
                                                "Client connected to Group Owner with IP $serverIp.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            Log.d(
                                                "WifiP2p",
                                                "Client connected to Group Owner with IP $serverIp."
                                            )
                                            ipAddress(serverIp)
                                            centralDataStore?.updateConnection(ConnectionSate.Connected.toString())


                                            dataExchange.client(serverIp)


                                        }

                                    }

                                }
                            })


                    }else{
                        centralDataStore?.updateConnection(ConnectionSate.Disconnected.toString())
                    }
                }
            }
        }
    }

    fun connect(device: WifiP2pDevice, response: (( WifiP2pDevice) -> Unit)={},failedresponse:((WifiP2pDevice)-> Unit)={}) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {

            return
        }

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }

        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                centralDataStore?.updateConnection(ConnectionSate.Connecting.toString())
               response(device)
                Log.d("WifiP2p", "Connecting.${device.deviceAddress}")


            }

            override fun onFailure(reason: Int) {
                failedresponse(device)
                centralDataStore?.updateConnection(ConnectionSate.Failed.toString())
                Log.d("WifiP2p", "Connectivity failed with code $reason.")
            }
        })
    }

    fun discoverPeers() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            Toast.makeText(context, "Missing permission for discovery.", Toast.LENGTH_SHORT).show()
            return
        }

        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {

                Log.d("WifiP2p", "Discovery started.")
            }

            override fun onFailure(reason: Int) {
                when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> {
                        Toast.makeText(context, "WiFi Direct not supported.", Toast.LENGTH_SHORT).show()
                        Log.d("WifiP2p", "WiFi Direct not supported.")
                    }
                    WifiP2pManager.BUSY -> {
                        Toast.makeText(context, "WiFi Direct is busy.", Toast.LENGTH_SHORT).show()
                        Log.d("WifiP2p", "WiFi Direct is busy.")
                    }
                    WifiP2pManager.ERROR -> {
                        Toast.makeText(context, "WiFi Direct internal error.", Toast.LENGTH_SHORT).show()
                        Log.d("WifiP2p", "WiFi Direct internal error.")
                    }
                    else -> {
                        Toast.makeText(context, "Discovery failed with code $reason.", Toast.LENGTH_SHORT).show()
                        Log.d("WifiP2p", "Discovery failed with code $reason.")
                    }
                }
            }
        })
    }

    fun createWifiDirectGroup() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED

        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            Toast.makeText(context, "Permission missing.", Toast.LENGTH_SHORT).show()
            centralDataStore?.updateIsGroupCreation(false)
            return
        }

        wifiP2pManager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(context, "Group is being created.", Toast.LENGTH_SHORT).show()
                Log.d("WifiP2p", "Group is being created.")
                centralDataStore?.updateIsGroupCreation(true)


            }

            override fun onFailure(reason: Int) {
                Toast.makeText(context, "Start Again.", Toast.LENGTH_SHORT).show()
                Log.d("WifiP2p", "Create group failed with code $reason.")
                removeGroup()
                centralDataStore?.updateIsGroupCreation(false)
            }
        })
    }



    fun removeGroup() {
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(context, "Group is Removed.", Toast.LENGTH_SHORT).show()
                Log.d("WifiP2p", "Group is Removed.")

            }

            override fun onFailure(reason: Int) {
                Toast.makeText(context, "Remove group failed.", Toast.LENGTH_SHORT).show()
                Log.d("WifiP2p", "Remove group failed with code $reason.")
            }
        })
    }

    fun registerReceiver() {
        context.registerReceiver(receiver, intentFilter)
        Log.d("WifiP2p", "Receiver registered.")
    }

    fun unregisterReceiver() {
        context.unregisterReceiver(receiver)
        Log.d("WifiP2p", "Receiver unregistered.")
    }


}


