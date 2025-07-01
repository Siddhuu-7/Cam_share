package com.example.foregroundservice

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.foregroundservice.ui.theme.ForegroundServiceTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {






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


    override fun onStart() {
        super.onStart()
        val intent= Intent(this, CounterService::class.java)
        bindService(intent,connection,BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        isBound=false
    }

    enum class AppState {
        IDLE,           // Neither service is running
        RECEIVING,      // Receive service is running
        SHARING         // Share mode is active
    }

    private var currentState = mutableStateOf(AppState.IDLE)
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var permissionLaunch: ActivityResultLauncher<String>

    lateinit var wifiP2pManager: WifiP2pManager
    lateinit var channel: WifiP2pManager.Channel
    private lateinit var wifiDirectManager: WifiDirectManager

    private val wbl = WBL()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val centralDataStore: CentralDataStore by viewModels()

        var nearByPeers: NearByPeers = NearByPeers(centralDataStore)

        wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)
        wifiDirectManager = WifiDirectManager(this@MainActivity,
            wifiP2pManager,
            channel,
            centralDataStore,
        ){
            Log.w("WIFIDIRECT","$it")

        }
        wifiDirectManager.registerReceiver()
        lifecycleScope.launch{
            wifiDirectManager.peers.collect { newList ->
                centralDataStore.updatePeers(newList)
            }

        }
        lifecycleScope.launch{
            centralDataStore.connection.collect {
                Log.d("CONNECTIONSTATE", "status:${it}")
            }
        }

        val wifiManager = this@MainActivity.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        val isWifiEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wifiManager.wifiState == WifiManager.WIFI_STATE_ENABLED
        } else {
            wifiManager.isWifiEnabled
        }

        if (!isWifiEnabled) {
            Toast.makeText(this@MainActivity, "Wi-Fi is disabled. Please enable Wi-Fi.", Toast.LENGTH_SHORT).show()
        }

        permissionLaunch = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                Log.d("MainActivity", "Permission still denied")
            }
        }

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            permissions.forEach { (permission, granted) ->
                if (!granted) {
                    Log.d("MainActivity", "Denied: $permission")

                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        showRationalePermission.value = permission
                        showDialog.value = true
                    } else {
                        permissionLaunch.launch(permission)
                    }
                }
            }
        }

        checkAndRequestPermissions()

        setContent {
            ForegroundServiceTheme {
                val permission = showRationalePermission.value

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Greeting(
                            onStartReceiveService = {
                                startReceiveService()
                            },
                            onStopReceiveService = {
                                stopReceiveService()
                            },
                            onStartShareMode = {
                                startShareMode()

                            },
                            onStopShareMode = {
                                stopShareMode()
                                wifiDirectManager.removeGroup()
                            },
                            currentState = currentState
                        )


                        if (currentState.value == AppState.RECEIVING) {
                            Spacer(modifier = Modifier.height(24.dp))

                            var failedDevice by remember { mutableStateOf<WifiP2pDevice?>(null) }

                            nearByPeers.DeviceListArea(

                                onRefresh = {
                                    wifiDirectManager.discoverPeers()
                                },
                                launqr = { device ->
                                    wifiDirectManager.connect(device,
                                        failedresponse = { it ->
                                            failedDevice = it
                                        }
                                    )
                                }
                            )
                        }
                    }

                    wbl.CheckAndAskServicesUI()

                    if (showDialog.value && permission != null) {
                        AlertDialog(
                            onDismissRequest = { showDialog.value = false },
                            title = { Text("Permission Required") },
                            text = { Text("This permission is needed to properly use the app.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    permissionLaunch.launch(permission)
                                    showDialog.value = false
                                }) {
                                    Text("Allow")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showDialog.value = false
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (myBoundService?.shareMode() == true) {
                        currentState.value = AppState.IDLE

                        AlertDialog(
                            onDismissRequest = { showDialog.value = false },
                            confirmButton = {
                                TextButton(onClick = { showDialog.value = false }) {
                                    Text("OK", color = Color.Red)
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = Color.Red
                                )
                            },
                            title = {
                                Text("Warning", color = Color.Red)
                            },
                            text = {
                                Text("Share Mode is currently ON. Please stop it before proceeding.")
                            }
                        )
                    }

                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        stopAllServices()
        getSharedPreferences("camshare",MODE_PRIVATE)
            .edit()
            .putBoolean("Sharing",false)
            .apply()

    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)


            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {

                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)


//                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
//                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
//                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)


                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> { // API 29 (Android 10) & API 30 (Android 11)
                // Permissions for Android 10 & 11
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)


            }
            else -> { // API 24 (Android 7) up to API 28 (Android 9)
                // Permissions for Android 7, 8, 9
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION) // CRITICAL for Bluetooth scanning on these versions
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }


    private fun stopAllServices() {
        val intent = Intent(this, CounterService::class.java).apply {
            action = "STOP_SHARE"
        }
        startService(intent)
        wifiDirectManager.removeGroup()
        wifiDirectManager.unregisterReceiver()
        currentState.value = AppState.IDLE
    }

    companion object {
        val showDialog = mutableStateOf(false)
        val showRationalePermission = mutableStateOf<String?>(null)
    }

    @Composable
    fun Greeting(
        onStartReceiveService: () -> Unit,
        onStopReceiveService: () -> Unit,
        onStartShareMode: () -> Unit,
        onStopShareMode: () -> Unit,
        currentState: MutableState<AppState>
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title with custom styling
            Text(
                text = "Instant Image Share",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))







            when (currentState.value) {
                AppState.IDLE -> {

                    Button(
                        onClick = onStartReceiveService,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Text(
                            text = "Start Receive Service",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

//                    Button(
//                        onClick = onStartShareMode,
//                        modifier = Modifier
//                            .fillMaxWidth(0.8f)
//                            .height(56.dp),
//                        shape = RoundedCornerShape(16.dp),
//                        colors = ButtonDefaults.buttonColors(
//                            containerColor = MaterialTheme.colorScheme.secondary,
//                            contentColor = MaterialTheme.colorScheme.onSecondary
//                        ),
//                        elevation = ButtonDefaults.buttonElevation(
//                            defaultElevation = 4.dp,
//                            pressedElevation = 8.dp
//                        )
//                    ) {
//                        Text(
//                            text = "Start Share Mode (Scan QR)",
//                            style = MaterialTheme.typography.bodyLarge.copy(
//                                fontWeight = FontWeight.Medium
//                            )
//                        )
//                    }
                }

                AppState.RECEIVING -> {

                    Button(
                        onClick = onStopReceiveService,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Text(
                            text = "Stop Image Receive Service",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }


                }

                AppState.SHARING -> {

                    Button(
                        onClick = onStopShareMode,
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Text(
                            text = "Stop Share Mode",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    DisplayImageFromAssets("sgnl.png")
                }
            }
        }
    }

    @Composable
    fun DisplayImageFromAssets(fileName: String) {
        val context = LocalContext.current
        val assetManager = context.assets


        val inputStream = assetManager.open(fileName)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR Code for connection",
                modifier = Modifier.size(228.dp)
            )
        }

    }

    private fun startReceiveService() {
        val serviceIntent = Intent(this, CounterService::class.java).apply {
            action = "START"
        }

        startService(serviceIntent)
        wifiDirectManager.discoverPeers()
        currentState.value = AppState.RECEIVING
        Log.d("MainActivity", "Started receive service")
    }

    private fun stopReceiveService() {
        val intent = Intent(this, CounterService::class.java).apply {
            action = "STOP"
        }
        startService(intent)

        wifiDirectManager.removeGroup()
        currentState.value = AppState.IDLE
        Log.d("MainActivity", "Stopped receive service")
    }

    private fun startShareMode() {
        val intent = Intent(this, CounterService::class.java).apply {
            action = "SHARE"
        }
        startService(intent)

        wifiDirectManager.createWifiDirectGroup()
//        currentState.value = AppState.SHARING
        Log.d("MainActivity", "Started share mode")
    }

    private fun stopShareMode() {
        val intent = Intent(this, CounterService::class.java).apply {
            action = "STOP_SHARE"
        }
        startService(intent)

        currentState.value = AppState.IDLE
        Log.d("MainActivity", "Stopped share mode")
    }
}