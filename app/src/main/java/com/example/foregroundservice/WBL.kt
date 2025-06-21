package com.example.foregroundservice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.padding


import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver


class WBL() {


    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            try {
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.LOCATION_MODE
                ) != Settings.Secure.LOCATION_MODE_OFF
            } catch (e: Settings.SettingNotFoundException) {
                false
            }
        }
    }


    private fun isWifiEnabled(context: Context): Boolean {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled ?: false
    }


//    private fun isBluetoothEnabled(context: Context): Boolean {
//        val bluetoothManager =
//            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
//        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
//        return bluetoothAdapter?.isEnabled ?: false
//    }



    @Composable
    fun CheckAndAskServicesUI() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current


        var isLocationServiceOn by remember { mutableStateOf(isLocationEnabled(context)) }
        var isWifiOn by remember { mutableStateOf(isWifiEnabled(context)) }
//        var isBluetoothOn by remember { mutableStateOf(isBluetoothEnabled(context)) }


        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isLocationServiceOn = isLocationEnabled(context)
                    isWifiOn = isWifiEnabled(context)
//                    isBluetoothOn = isBluetoothEnabled(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }


        val missingServices = remember(isLocationServiceOn, isWifiOn) {
            mutableListOf<String>().apply {
                if (!isLocationServiceOn) add("Location")
                if (!isWifiOn) add("Wi-Fi")
//                if (!isBluetoothOn) add("Bluetooth")
            }
        }


        var showDialog = missingServices.isNotEmpty()

        if (showDialog) {
            AlertDialog(
                onDismissRequest = {

                    Log.d("ServiceCheck", "AlertDialog dismissed without action.")

                },
                title = {
                    Text(
                        "Action Required: Enable Services",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column {
                        Text(
                            "Please enable the following services to use the app:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        missingServices.forEach { serviceName ->
                            Text(
                                text = "• $serviceName",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {

                        when {
                            !isLocationServiceOn -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            !isWifiOn -> context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
//                            !isBluetoothOn -> context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }

                    }) {
                        Text("Go to Settings")
                    }
                }

            )
        }
    }
}