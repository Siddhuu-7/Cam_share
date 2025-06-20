package com.example.foregroundservice

import android.net.wifi.p2p.WifiP2pDevice
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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

class NearByPeers(
    private val centralDataStore: CentralDataStore? = null
) {



    @Composable
    fun DeviceListArea(
        onRefresh: () -> Unit,
        launqr: (WifiP2pDevice) -> Unit,

    ) {


        val peerList = centralDataStore?.peers?.collectAsState(emptyList())?.value ?: emptyList()

        LaunchedEffect(peerList) {
            Log.d("PeerListTest", "Peer list updated: ${peerList.joinToString { it.deviceName ?: "Unknown" }}")
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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Section
                DeviceDiscoveryHeader(
                    deviceCount = peerList.size,
                    onRefresh = onRefresh
                )

                Spacer(modifier = Modifier.height(20.dp))

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
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon with badge
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )

                if (deviceCount > 0) {
                    Badge(
                        modifier = Modifier.offset(x = 2.dp, y = (-2).dp),
                        containerColor = MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            text = deviceCount.toString(),
                            color = MaterialTheme.colorScheme.onError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Nearby Devices",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (deviceCount == 0) "Searching for devices..."
                else "$deviceCount device${if (deviceCount == 1) "" else "s"} found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

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

        Button(
            onClick = {
                isRefreshing = true
                onClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer(rotationZ = rotation)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRefreshing) "Searching..." else "Refresh",
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
                .shadow(4.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
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
                    DeviceList(
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
                    .size(64.dp)
                    .graphicsLayer(alpha = pulseAlpha),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "No Devices Found",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Make sure Wi-Fi Direct is enabled on nearby devices",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    @Composable
    private fun DeviceList(
        devices: List<WifiP2pDevice>,
        onDeviceClick: (WifiP2pDevice) -> Unit
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // List Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Available Devices",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${devices.size} found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Device List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(devices, key = { _, device -> device.deviceAddress }) { index, device ->
                    DeviceListItem(
                        device = device,
                        index = index + 1,
                        onClick = {
                            Log.d("DeviceListArea", "Clicked device: ${device.deviceName}")
                            onDeviceClick(device)
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun DeviceListItem(
        device: WifiP2pDevice,
        index: Int,
        onClick: () -> Unit
    ) {
        val connectionState = centralDataStore?.connection?.collectAsState("")?.value ?: ""

        var isPressed by remember { mutableStateOf(false) }
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.98f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .then(
                    if (connectionState != "Connected") {
                        Modifier.clickable {
                            isPressed = true
                            onClick()
                        }
                    } else Modifier
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isPressed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Index Badge
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(32.dp),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Device Icon
                DeviceIcon(
                    deviceName = device.deviceName,
                    deviceType = getDeviceTypeIcon(device)
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Device Info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = device.deviceName ?: "Unknown Device",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = device.deviceAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    ConnectionStatusChip()
                }

                // Arrow Icon
                if (connectionState != "Connected") {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
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
    private fun DeviceIcon(
        deviceName: String,
        deviceType: ImageVector
    ) {
        Box(
            modifier = Modifier.size(40.dp),
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
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        CircleShape
                    )
            )

            if (deviceName?.isNotBlank() == true) {
                Text(
                    text = deviceName.first().uppercaseChar().toString(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = deviceType,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    @Composable
    private fun ConnectionStatusChip() {
        val connectionState = centralDataStore?.connection?.collectAsState("")?.value ?: ""
        val statusText = if (connectionState.isNotBlank()) connectionState else "Available"

        val statusColor = when (statusText) {
            "Connected" -> Color(0xFF4CAF50)
            "Connecting..." -> MaterialTheme.colorScheme.primary
            "Failed" -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = statusColor.copy(alpha = 0.12f)
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = statusColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }

    private fun getDeviceTypeIcon(device: WifiP2pDevice): ImageVector {
        return when {
            device.deviceName?.contains("phone", ignoreCase = true) == true -> Icons.Default.Phone
            else -> Icons.Default.Info
        }
    }
}