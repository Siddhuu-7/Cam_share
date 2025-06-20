package com.example.foregroundservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent

import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.io.File
import java.io.FileOutputStream

import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetAddress

open class TCp(
    private val serviceScope: CoroutineScope,
    private val context: Context
) {
    private var serverSocket: ServerSocket? = null
    private val fileOperation = FileOperation(context)
    private val serverPort = 6961

    suspend fun createServer() {
        try {
            serverSocket = ServerSocket(serverPort).apply {
                soTimeout = 1000 // 1-second timeout to enable shutdown
            }

            Log.d("CounterService", "Server started on port $serverPort")

            while (serviceScope.isActive) {
                try {
                    val client = serverSocket!!.accept()
                    serviceScope.launch {
                        handleClientSafely(client)
                    }
                } catch (e: SocketTimeoutException) {
                    // Loop again to check for isActive
                }
            }
        } catch (e: Exception) {
            Log.e("CounterService", "Server error: ${e.message}")
        } finally {
            serverSocket?.close()
            Log.d("CounterService", "Server stopped")
        }
    }
    fun stopServer(){
        serverSocket?.close()
    }
    private suspend fun handleClientSafely(client: Socket) {
        try {
            handleClient(client)
        } catch (e: Exception) {
            Log.e("CounterService", "Client handling error: ${e.message}")
        }
    }

    private suspend fun handleClient(client: Socket) {
        client.use { socket ->
            val path = fileOperation.createFolderInDownloads("Cam Share")?.absolutePath
            if (path == null) {
                Log.e("CounterService", "Unable to create directory")
                return
            }
            fileOperation.saveFileFromClient(path, socket)
        }
    }

    fun sendImageToServer(serverIp: String, context: Context, imageUri: Uri?) {
        if (imageUri == null) {
            Log.e("TCPClient", "Image URI is null.")
            return
        }

        var socket: Socket? = null
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        try {
            socket = Socket(serverIp, serverPort)
            inputStream = context.contentResolver.openInputStream(imageUri)
            outputStream = socket.getOutputStream()

            if (inputStream == null) {
                Log.e("TCPClient", "Unable to open InputStream.")
                return
            }

            val buffer = ByteArray(4096)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            outputStream.flush()
            Log.d("TCPClient", "Image sent successfully.")
        } catch (e: Exception) {
            Log.e("TCPClient", "Error sending image: ${e.message}", e)
        } finally {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        }
    }
}

class FileOperation(private val context: Context) {

    fun createFolderInDownloads(folderName: String): File? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val folder = File(downloadsDir, folderName)

        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                Log.e("FileOperation", "Unable to create directory.")
                return null
            }
        }

        return folder
    }

    suspend fun saveFileFromClient(path: String, socket: Socket) {
        var inputStream: InputStream? = null
        var fileOutputStream: FileOutputStream? = null

        try {
            inputStream = socket.getInputStream()
            val buffer = ByteArray(4096)
            var bytesRead: Int

            val timestamp = System.currentTimeMillis()
            val fileName = "IMG_$timestamp.jpg"

            val file = File(path, fileName)
            fileOutputStream = FileOutputStream(file)

            // Loop to read until the stream reaches the end
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                fileOutputStream.write(buffer, 0, bytesRead)
            }

            fileOutputStream.flush()
            Log.d("FileOperation", "File received and saved successfully.")
            saveMediaStoreImg(context, file, fileName, "Cam Share")
            showFileReceivedNotification(context, fileName)
        } catch (e: Exception) {
            Log.e("FileOperation", "Error while saving file.", e)
        } finally {
            inputStream?.close()
            fileOutputStream?.close()
        }
    }

    private fun showFileReceivedNotification(context: Context, fileName: String) {
        val channelId = "file_received_channel"
        val channelName = "File Received"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("File Received")
            .setContentText("Image $fileName saved successfully.")
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun saveMediaStoreImg(
        context: Context,
        file: File,
        fileName: String,
        albumName: String = "Foreground"
    ) {
        var fos: OutputStream? = null
        var imageUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        if (fileName.endsWith(".png")) "image/png" else "image/jpeg"
                    )
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + File.separator + albumName
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                imageUri?.let { uri ->
                    fos = resolver.openOutputStream(uri)
                    fos?.use { outputStream ->
                        file.inputStream().copyTo(outputStream)
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    Log.d("MediaStore", "Image updated in MediaStore successfully.")
                } ?: throw Exception("MediaStore Uri was null")

            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES + File.separator + albumName
                )
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                val destFile = File(imagesDir, fileName)
                file.copyTo(destFile, overwrite = true)
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)))
                Toast.makeText(context, "Image saved to Gallery.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (imageUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.delete(imageUri, null, null)
            }
            Toast.makeText(context, "Error saving image.", Toast.LENGTH_LONG).show()
        } finally {
            fos?.close()
        }
    }


}
class DataExchange(
    private val serviceScope: CoroutineScope,
    private val context: Context
) {
    private val serverPort = 8888
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val localIP = getLocalIpAddress(context)

    fun startServer() {
        if (serverJob?.isActive == true) {
            Log.d("DataExchange", "Server already running")
            return
        }

        serverJob = serviceScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(serverPort).apply {
                    soTimeout = 1000 // 1s timeout to check cancellation
                }
                Log.d("DataExchange", "Server started on port $serverPort")

                while (isActive) {
                    try {
                        val client = serverSocket!!.accept()
                        launch {
                            val inputStream = client.getInputStream()
                            val metadataReader = BufferedReader(InputStreamReader(inputStream))
                            val metadata = metadataReader.readLine()
                            Log.d("MetaData", metadata ?: "No metadata received")
                            client.close()
                        }
                    } catch (e: SocketTimeoutException) {
                        // Timeout is expected
                    } catch (e: Exception) {
                        Log.e("DataExchange", "Accept error: ${e.message}")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("DataExchange", "Server error: ${e.message}")
            } finally {
                try {
                    serverSocket?.close()
                    Log.d("DataExchange", "Server socket closed")
                } catch (e: Exception) {
                    Log.e("DataExchange", "Close error: ${e.message}")
                }
            }
        }
    }

    fun stopServer() {
        try {
            serverJob?.cancel()
            serverSocket?.close()
            Log.w("DataExchange", "Server stop requested")
        } catch (e: Exception) {
            Log.e("DataExchange", "Server stop failed: ${e.message}")
        }
    }

    fun client(metaData: String?) {
        serviceScope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket(localIP, serverPort)
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(metaData)
                Log.d("Client", "Metadata sent: $metaData")
            } catch (e: Exception) {
                Log.e("Client", "Error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun getLocalIpAddress(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wifiManager.connectionInfo.ipAddress
        if (ipInt == 0) return null
        return InetAddress.getByAddress(
            byteArrayOf(
                (ipInt and 0xff).toByte(),
                (ipInt shr 8 and 0xff).toByte(),
                (ipInt shr 16 and 0xff).toByte(),
                (ipInt shr 24 and 0xff).toByte()
            )
        ).hostAddress
    }
}
