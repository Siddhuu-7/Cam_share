package com.example.foregroundservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    fun sendImageToServer(serverIp: String, context: Context, imageUri: Uri?): Boolean {
        if (imageUri == null) {
            Log.e("TCPClient", "Image URI is null.")
            return false
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
                return false
            }

            val buffer = ByteArray(4096)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            outputStream.flush()
            Log.d("TCPClient", "Image sent successfully.")
            return true

        } catch (e: IllegalStateException) {
            Log.e("TCPClient", "IllegalStateException: ${e.message}", e)
            return false
        } catch (e: Exception) {
            Log.e("TCPClient", "Error sending image: ${e.message}", e)
            return false
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                Log.w("TCPClient", "Error closing resources: ${e.message}", e)
            }
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
            .setSound(null)
            .build()


        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun saveMediaStoreImg(
        context: Context,
        file: File,
        fileName: String,
        albumName: String
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





   suspend fun startServer(ipAddress:((String)-> Unit) ={}){
        try {
            serverSocket = ServerSocket(serverPort).apply {
                soTimeout = 1000 // 1-second timeout to enable shutdown
            }

            Log.d("CounterService", "Server started on port $serverPort")

            while (serviceScope.isActive) {
                try {
                    val client = serverSocket!!.accept()
                    serviceScope.launch {
                        var inputStream=client.getInputStream()
                        var line= BufferedReader(InputStreamReader(inputStream))
                        val receivedData = line.readLine().split(":")
                        val output= PrintWriter(client.getOutputStream(),true)
                        val clientdevice=receivedData[0]
                        val localIP=receivedData[1]
                        showConnectiondNotification(clientdevice)
                        Log.v("DATAEXCHANGE"," server Recived Data${receivedData[0]}:${receivedData[1]}")
                        if (localIP != "null") {
                            ipAddress(localIP)
                        }
                        var model= Build.MODEL
                        var manufacturer= Build.MANUFACTURER
                        var device="$model $manufacturer"
                        output.println(device)

                    }
                } catch (e: SocketTimeoutException) {
                    // Loop again to check for isActive
                }
            }
        } catch (e: Exception) {
            Log.e("DATA EXCHANGE", "Server error: ${e.message}")
        } finally {
            serverSocket?.close()
            Log.d("DATACENTER", "Server stopped port 8888")
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
            Log.w("DataExchange", "Server stop requested")
        } catch (e: Exception) {
            Log.e("DataExchange", "Server stop failed: ${e.message}")
        }
    }

    fun client(serverIP: String?) {
            var model= Build.MODEL
            var manufacturer= Build.MANUFACTURER


        serviceScope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket(serverIP, serverPort)
                var localIP=socket.localAddress.hostAddress!!
                var device="$model $manufacturer:$localIP"
                val writer = PrintWriter(socket.getOutputStream(), true)
                Log.d("Client", "Metadata sent: $serverIP:$localIP")
                writer.println(device)
                var inputStream=socket.getInputStream()
                var line= BufferedReader(InputStreamReader(inputStream))
                val receivedData = line.readLine()
                showConnectiondNotification(receivedData)
                Log.v("DATAEXCHANGE"," client Recived Data${receivedData}")
            } catch (e: Exception) {
                Log.e("Client", "Error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun showConnectiondNotification(device: String) {
        val channelId = "Connection1234"
        val channelName = "Connection"
        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        val intent= Intent(context, MainActivity::class.java).apply {
            flags= Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent=PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context,channelId)
            .setContentTitle("Cam Share")
            .setContentText("Connected to ${device}")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
