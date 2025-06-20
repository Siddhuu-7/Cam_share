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

    private val binder =LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val CHANNEL_ID = "counter_service_channel"
    private lateinit var tcpServer: TCp

    private var contentObserver: MediaStoreChanges? = null

    private val UriQueue: ConcurrentLinkedQueue<Uri> = ConcurrentLinkedQueue()
    private val seenUris: MutableSet<Uri> = Collections.synchronizedSet(mutableSetOf())
    private val ipqueues : ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()


    private var ip: String? = null
    private var isServerRunning=false
    private var isShareModeon=false
inner class LocalBinder: Binder(){
    fun getService(): CounterService=this@CounterService
}
    fun setIpAddress(Ip: String){
       ip=Ip
        Log.wtf("COUNTERSERVICE","IP is ASSESING TO ip $Ip")
    }
    override fun onBind(intent: Intent) =binder

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action



        if(!ipqueues.contains(ip.toString())){
            ipqueues.add(ip.toString())
        }
        Log.d("CounterService", "Service started with action: $action")

        when (action) {
            "START" -> {

                if (!isShareModeon){
                    isServerRunning=true
                    startForeground(1, buildNotification("Connection Established 🛜..."))


                    serviceScope.launch {
                        try {
                            tcpServer = TCp(serviceScope, this@CounterService)
                            tcpServer.createServer()

                        } catch (e: Exception) {
                            Log.e("CounterService", "Server start failed.", e)
                        } finally {

                        }
                    }
                }


                }


            "SHARE" -> {
              if (!isServerRunning){

                  isShareModeon=true
                  try {
                      startForeground(1, buildNotification("File sharing started..."))
                      Log.d("IPAddress",ip.toString())
                  } catch (e: Exception) {
                      Log.e("CounterService", "Failed to start foreground service", e)
                  }
                  if (!::tcpServer.isInitialized) {
                      tcpServer = TCp(serviceScope, this@CounterService)
                  }

                  try {
                      contentObserver = MediaStoreChanges(this@CounterService) { uri ->
                          serviceScope.launch{
                              try {

                                  delay(8000)
                                  if (
                                      uri != null &&
                                      seenUris.add(uri)

                                  ) {
                                      Log.d("CounterService", "New image received: $uri")
                                      Log.w("COUNTERSERVICE","sending to $ip")
                                      tcpServer.sendImageToServer(ip!!, this@CounterService, uri)

                                  }

                              } catch (e: Exception) {
                                  Log.e("CounterService", "Error in content observer callback", e)
                              }
                          }
                      }

                      contentObserver?.register()

                  } catch (e: Exception) {
                      Log.e("CounterService", "Failed to register content observer", e)
                  }
              }




            }

            "STOP_SHARE" -> {
                    isShareModeon=false
                    contentObserver?.let {
                        it.unregister()
                        contentObserver = null
                    }


                    startForeground(1, buildNotification("File sharing stopped"))

            }

            "STOP" -> {
               isServerRunning=false
                stopSelf()
                tcpServer.stopServer()
                startForeground(1, buildNotification("Connection stopped 👾..."))
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }


    override fun onDestroy() {
        Log.d("CounterService", "Service destroyed")
        serviceScope.cancel()



        try {

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
                "Instant Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(context: String = "Instant Server running..."): Notification {
             val intent= Intent(this, MainActivity::class.java).apply {
                 flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
             }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Instant Share Service")
            .setContentText(context)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .build()
    }




}
