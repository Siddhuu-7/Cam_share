package com.example.foregroundservice

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.foregroundservice.ui.theme.ForegroundServiceTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.*
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*

class MainActivity : ComponentActivity() {
    private var isserverRunning = mutableStateOf(false)
    private var isShareModeOn = mutableStateOf(false)
    private  var isShareStarted =mutableStateOf(false)
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var permissionLaunch: ActivityResultLauncher<String>
    private var ipAddress=mutableStateOf<String?>(null)
    lateinit var wifiP2pManager: WifiP2pManager
    lateinit var channel: WifiP2pManager.Channel
    private lateinit var wifiDirectManager: WifiDirectManager

    fun updateAddress(newIP:String){
        ipAddress.value=newIP
    }
    fun getIPaddress(): String?{
        return ipAddress.value
    }







    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)
        wifiDirectManager = WifiDirectManager(this@MainActivity, wifiP2pManager, channel)
        wifiDirectManager.registerReceiver()
        wifiDirectManager.discoverPeers()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                wifiDirectManager.peers.collect { peerList ->
                    if (peerList.isEmpty()) {
                        Log.d("WiFiDirect", "No peers found")
                    } else {
                        peerList.forEach { device ->
                            Log.d("WiFiDirect", "Device: ${device.deviceName} - ${device.deviceAddress}")
                        }
                    }
                }
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
                val context = LocalContext.current
                val showScanner = remember { mutableStateOf(false) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Greeting(
                            onStartService = {
                                navigateTowifi()
                                startCounterService() },
                            OnStopService = { stopCounterService() },
                            isserverRunning,
                            onShareModeOn = {
                                navigateTowifi()
                                showScanner.value = true

                            },
                            onShowscanner = {
                                if(showScanner.value)showScanner.value = false
                                else showScanner.value = true
                            },
                            onShareModeOff = {
                                stopShareMode()
                                showScanner.value = false
                            },
                            isShareModeOn
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        if (isserverRunning.value) {
                            val ipAddress = getDeviceIpAddress() ?: "Unknown IP"
                            Text("Device IP: $ipAddress", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(12.dp))
                            QrCodeDisplay(ipAddress, size = 500)
                        }

                        if (showScanner.value) {
                            isShareModeOn.value = true
                            QRScannerView(context = context) { result ->
                                showScanner.value = false
                                updateAddress(result)
                                shareImages()
                                Log.d("QRScanner", "Scanned result: $result")

                            }
                        }

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
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiDirectManager.unregisterReceiver()
    }
    private fun getDeviceIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in Collections.list(interfaces)) {
                val addrs = intf.inetAddresses
                for (addr in Collections.list(addrs)) {
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val ip = addr.hostAddress
                        if (ip.indexOf(':') < 0) return ip
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("MainActivity", "Error getting IP address", ex)
        }
        return null
    }

    private fun checkAndRequestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.CAMERA,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        permissionLauncher.launch(permissions)
    }

    private fun startCounterService() {
        val serviceIntent = Intent(this, CounterService::class.java).apply {
            action = "START"
        }
        startService(serviceIntent)
        isserverRunning.value = true
    }

    private fun stopCounterService() {
        val intent = Intent(this, CounterService::class.java).apply {
            action = "STOP"
        }
        startService(intent)
        isserverRunning.value = false
    }
    private fun navigateTowifi(){
        val intent = Intent()
        intent.component = ComponentName(
            "com.android.settings",
            "com.android.settings.wifi.p2p.WifiP2pSettings"
        )
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Fallback to general Wi-Fi settings if specific Wi-Fi Direct page not found
            startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
        }
    }
    private fun shareImages() {
        val intent = Intent(this, CounterService::class.java).apply {
            action = "SHARE"
        }


        if(getIPaddress()!=null||getIPaddress()!=""){
            isShareStarted.value=true
            intent.putExtra("IpAddress",getIPaddress())
            startService(intent)
            updateAddress("")
        }

        isShareModeOn.value = true
    }
    fun showscanner(showScanner: MutableState<Boolean>){
        showScanner.value=true
    }
    private fun stopShareMode() {
        val intent = Intent(this, CounterService::class.java).apply {
            action = "STOP_SHARE"
        }
        if(isShareStarted.value){
            startService(intent)
        }
        isShareModeOn.value = false
    }

    companion object {
        val showDialog = mutableStateOf(false)
        val showRationalePermission = mutableStateOf<String?>(null)
    }
}
@Composable
fun Greeting(
    onStartService: () -> Unit,
    OnStopService: () -> Unit,
    isserverRunning: MutableState<Boolean>,
    onShareModeOn: () -> Unit,
    onShowscanner:()-> Unit,
    onShareModeOff: () -> Unit,
    isShareModeOn: MutableState<Boolean>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Foreground Counter Service", style = MaterialTheme.typography.headlineMedium)

        if (!isserverRunning.value && !isShareModeOn.value) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onStartService) {
                Text("Start Counter Service")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onShareModeOn) {
                Text("Start Share Mode")
            }
        } else {
            val localSanner: MutableState<Boolean> =remember { mutableStateOf(false) }
            if (isserverRunning.value) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = OnStopService) {
                    Text("Stop Counter Service")
                }
            } else {

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onShareModeOff) {
                    Text("Stop Share Mode")
                }
//                Button(onClick = { onShowscanner()
//                localSanner.value=!localSanner.value
//                }) {
//                    Text(if(localSanner.value) "Show Scanner" else "Hide Scanner")
//                }
            }
        }
    }
}

@Composable
fun QrCodeDisplay(data: String, size: Int = 500) {
    val qrBitmap = remember(data) { generateQRCode(data, size, size) }

    Image(
        bitmap = qrBitmap.asImageBitmap(),
        contentDescription = "QR Code",
        modifier = Modifier
            .size(size.dp)
            .padding(16.dp)
    )
}

fun generateQRCode(text: String, width: Int, height: Int): Bitmap {
    val bitMatrix: BitMatrix = MultiFormatWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        width,
        height
    )

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}

@Composable
fun QRScannerView(
    context: Context,
    onResult: (String) -> Unit
) {
    var hasScanned by remember { mutableStateOf(false) }

    AndroidView(
        factory = {
            val scanner = DecoratedBarcodeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                decodeContinuous(object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult?) {
                        if (!hasScanned && result != null) {
                            hasScanned = true
                            onResult(result.text)
                        }
                    }

                    override fun possibleResultPoints(resultPoints: List<com.google.zxing.ResultPoint>) {}
                })
            }
            scanner.resume()
            scanner
        },
        update = { it.resume() }
    )

}
