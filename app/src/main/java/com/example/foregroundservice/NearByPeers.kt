package com.example.foregroundservice

import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class NearByPeers {


private var connectedDeciivesAddress: WifiP2pDevice?=null
    private var faileddeciivesAddress=mutableStateOf<String?>("")
    @Composable
    fun DeviceListArea(
       manager: WifiDirectManager,
        onRefresh: () -> Unit,
        launqr: (WifiP2pDevice) -> Unit,
       connecteddeviceaddress : WifiP2pDevice ?=null,
       connectionfailaddrtess : String ?=null

    ) {
        connectedDeciivesAddress=connecteddeviceaddress
        faileddeciivesAddress.value=connectionfailaddrtess
//        if (connectedDeciivesAddress.value!=null)

        var peerList by remember { mutableStateOf<List<WifiP2pDevice>>(emptyList()) }
        LaunchedEffect(manager) {
            manager.peers.collect { newList ->
                peerList = newList
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Section
                DeviceDiscoveryHeader(
                    deviceCount = peerList.size,
                    onRefresh = onRefresh
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Device List Section
                DeviceDiscoveryContent(
                    peerList = peerList,
                    onDeviceClick = launqr
                )
            }
        }
    }

    @Composable
    private fun DeviceDiscoveryHeader(
        deviceCount: Int,
        onRefresh: () -> Unit
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon with badge
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
                        .padding(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )

                if (deviceCount > 0) {
                    Badge(
                        modifier = Modifier.offset(x = 4.dp, y = (-4).dp),
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            text = deviceCount.toString(),
                            color = MaterialTheme.colorScheme.onError,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Device Discovery",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (deviceCount == 0) "Searching for nearby devices…"
                else "$deviceCount device${if (deviceCount == 1) "" else "s"} found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            RefreshButton(onClick = onRefresh)
        }
    }


    @Composable
    private fun RefreshButton(onClick: () -> Unit) {
        var isRefreshing by remember { mutableStateOf(false) }
        val rotation by animateFloatAsState(
            targetValue = if (isRefreshing) 360f else 0f,
            animationSpec = tween(1000, easing = LinearEasing),
            finishedListener = { isRefreshing = false }
        )
        Log.d("CONNECTEDADDRESS","${connectedDeciivesAddress?.status}")
        Button(
            onClick = {
                isRefreshing = true
                onClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer(rotationZ = rotation)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRefreshing) "Searching..." else "Refresh Devices",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }

    @Composable
    private fun DeviceDiscoveryContent(
        peerList: List<WifiP2pDevice>,
        onDeviceClick: (WifiP2pDevice) -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()

                .shadow(6.dp, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            AnimatedContent(
                targetState = peerList.isEmpty(),
                transitionSpec = {
                    slideInVertically() + fadeIn() togetherWith
                            slideOutVertically() + fadeOut()
                }
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyDeviceState()
                } else {
                    DeviceGrid(
                        devices = peerList,
                        onDeviceClick = onDeviceClick
                    )
                }
            }
        }
    }

    @Composable
    private fun EmptyDeviceState() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated searching icon
            val infiniteTransition = rememberInfiniteTransition()
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer(alpha = pulseAlpha),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "No Devices Found",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Make sure Wi-Fi Direct is enabled on nearby devices and try refreshing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    @Composable
    private fun DeviceGrid(
        devices: List<WifiP2pDevice>,
        onDeviceClick: (WifiP2pDevice) -> Unit
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(devices, key = { it.deviceAddress }) { device ->
                DeviceCard(
                    device = device,
                    onClick = {
                        Log.d("DeviceListArea", "Clicked device: ${device.deviceName}")
                        onDeviceClick(device)
                    }
                )
            }
        }
    }

    @Composable
    private fun DeviceCard(
        device: WifiP2pDevice,
        onClick: () -> Unit
    ) {
        var isPressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )

        Card(
            modifier = Modifier
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clickable {
                    isPressed = true
                    onClick()
                }
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Device Avatar
                DeviceAvatar(
                    deviceName = device.deviceName,
                    deviceType = getDeviceTypeIcon(device)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Device Name
                Text(
                    text = device.deviceName ?: "Unknown Device",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))


                ConnectionStatusChip(device = device)
            }
        }

        LaunchedEffect(isPressed) {
            if (isPressed) {
                kotlinx.coroutines.delay(150)
                isPressed = false
            }
        }
    }

    @Composable
    private fun DeviceAvatar(
        deviceName: String,
        deviceType: ImageVector
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        ),
                        CircleShape
                    )
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        CircleShape
                    )
            )


            if (deviceName.isNotBlank()) {
                Text(
                    text = deviceName.first().uppercaseChar().toString(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = deviceType,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    @Composable
    private fun ConnectionStatusChip(device: WifiP2pDevice) {
        val (statusText, statusColor) = when {
            device.deviceAddress == connectedDeciivesAddress?.deviceAddress -> "Connected" to Color(0xFF4CAF50)
            device.status == WifiP2pDevice.AVAILABLE -> "Available" to MaterialTheme.colorScheme.primary
            device.deviceAddress == faileddeciivesAddress.value-> "Failed" to MaterialTheme.colorScheme.error
            device.status == WifiP2pDevice.UNAVAILABLE -> "Unavailable" to MaterialTheme.colorScheme.outline
            else -> "Available" to MaterialTheme.colorScheme.outline
        }


        Surface(
            shape = RoundedCornerShape(12.dp),
            color = statusColor.copy(alpha = 0.15f),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = statusColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }

    private fun getDeviceTypeIcon(device: WifiP2pDevice): ImageVector {
        return when {
            device.deviceName.contains("phone", ignoreCase = true) -> Icons.Default.Phone

            else -> Icons.Default.Info
        }
    }
}