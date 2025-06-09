package com.example.foregroundservice

import android.app.*

import android.content.Intent
import android.os.Binder
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.ServerSocket
import  java.util.concurrent.ConcurrentLinkedQueue
import android.net.Uri

import java.util.Collections

class CounterService : Service() {

    private val binder = Binder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val CHANNEL_ID = "counter_service_channel"
    private lateinit var tcpServer: TCp
    private lateinit var contentObserver: MediaStoreChanges
    private val UriQueue: ConcurrentLinkedQueue<Uri> = ConcurrentLinkedQueue()
    private val seenUris: MutableSet<Uri> = Collections.synchronizedSet(mutableSetOf())
    private val ipqueues : ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    private var serverSocket: ServerSocket? = null
    private var serverRunning = false

    override fun onBind(intent: Intent) = binder

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val ip=intent?.getStringExtra("IpAddress")
        if(!ipqueues.contains(ip.toString())){
            ipqueues.add(ip.toString())
        }
        Log.d("CounterService", "Service started with action: $action")

        when (action) {
            "START" -> {
                startForeground(1, buildNotification("TCP Server running..."))
                serverRunning = true

                serviceScope.launch {
                    tcpServer = TCp(serviceScope, this@CounterService)

                    tcpServer.createServer()
                }
            }

            "SHARE" -> {
                try {
                    startForeground(1, buildNotification("File sharing started..."))
                    Log.d("IPAddress",ip.toString())
                } catch (e: Exception) {
                    Log.e("CounterService", "Failed to start foreground service", e)
                }

                try {
                    contentObserver = MediaStoreChanges(this@CounterService) { uri ->
                     serviceScope.launch{
                         try {
//                            val currentId = extractIdFromUri(uri.toString())
//                            val firstSeen = seenUris.firstOrNull()
//                            val firstSeenId = firstSeen?.toString()?.let { extractIdFromUri(it)
                                delay(8000)
                             if (
                                 uri != null &&
                                 seenUris.add(uri)

                             ) {

                                 Log.d("CounterService", "New image received: $uri")
//                                 UriQueue.add(uri)
                                 tcpServer.sendImageToServer(ip.toString(), this@CounterService, uri)
//                                 val ipThreads= CoroutineScope(SupervisorJob() + Dispatchers.IO)
//                                 for(ip in ipqueues){
//                                     ipThreads.launch{
//                                         tcpServer.sendImageToServer(ip, this@CounterService, uri)
//                                     }
//                                 }
//
//                                 ipThreads.cancel()

                             }

                         } catch (e: Exception) {
                             Log.e("CounterService", "Error in content observer callback", e)
                         }
                     }
                    }

                    contentObserver.register()

                } catch (e: Exception) {
                    Log.e("CounterService", "Failed to register content observer", e)
                }

                serviceScope.launch {
                    try {
                        if (!::tcpServer.isInitialized) {
                            tcpServer = TCp(serviceScope, this@CounterService)

                        }

                        Log.d("IMGURIS", "Uri Loading")

                        while (isActive) {
                            try {
                                var uri: Uri? = null

                                if (UriQueue.isNotEmpty()) {
                                    Log.d("IMGURIS", "Uri Loaded")
                                    uri = UriQueue.poll()

                                    if (uri != null) {
                                        try {
//                                            tcpServer.sendImageToServer("192.168.55.103", this@CounterService, uri)
                                            Log.d("IMGURIS", "Image sent: $uri")
                                        } catch (e: Exception) {
                                            Log.e("CounterService", "Failed to send image to server: $uri", e)
                                        }
                                    }
                                }

                            } catch (e: Exception) {
                                Log.e("CounterService", "Error while polling or sending URI", e)
                            }
                        }

                    } catch (e: Exception) {
                        Log.e("CounterService", "Error during TCP server initialization or loop", e)
                    }
                }

            }

            "STOP_SHARE" -> {
                contentObserver.unregister()
                startForeground(1, buildNotification("File sharing stopped"))
//                stopSelf()
            }

            "STOP" -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }


    override fun onDestroy() {
        Log.d("CounterService", "Service destroyed")
        serviceScope.cancel()



        try {
            serverSocket?.close()
            Log.d("CounterService", "Server socket closed")
        } catch (e: Exception) {
            Log.e("CounterService", "Error closing socket: ${e.message}")
        }

        super.onDestroy()
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Counter Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(context: String = "TCP Server running..."): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Counter Service")
            .setContentText(context)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
    fun extractIdFromUri(uriString: String): Long? {
        return try {
            val uri = Uri.parse(uriString)
            uri.lastPathSegment?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }



}
