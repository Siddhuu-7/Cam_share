package com.example.foregroundservice


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build

import java.io.File
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


class TCp (private var serviceScope: CoroutineScope,context: Context){
    private var serverSocket: ServerSocket? = null
    private val fileOperation = FileOperation(context)
    private val serverPort=6961
     suspend fun createServer() {
        try {
            serverSocket = ServerSocket(6961).apply {
                soTimeout = 1000
            }

            Log.d("CounterService", "Server started on port 6961")

            while (serviceScope.isActive) {
                try {
                    val client: Socket = serverSocket!!.accept()
                    handleClient(client)
                } catch (e: SocketTimeoutException) {
//                    Log.d("CounterService", "Socket timeout")
                }
            }

        } catch (e: Exception) {
            Log.e("CounterService", "Server error: ${e.message}")
        } finally {
            serverSocket?.close()
            Log.d("CounterService", "Server stopped")
        }
    }

    private fun handleClient(client: Socket) {
        serviceScope.launch {
            try {
                client.use { socket ->
                    val path: String
                    try {
                         path=fileOperation.createFolderInDownloads("Foreground").toString()
                        fileOperation.insertFile(path,socket)
                    }catch (e: Exception){
                        Log.e("CounterService", "Error creating folder: ${e.message}")
                    }


                }
            } catch (e: Exception) {
                Log.e("CounterService", "Client handling error: ${e.message}")
            }
        }
    }

    fun sendImageToServer(serverIp:String,context: Context, imageUri: Uri?) {
        val serverIp = serverIp

        if (imageUri == null) {
            Log.e("TCPClient", "Image URI is null.")
            return
        }

        var socket: Socket? = null
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null

        try {
            Log.d("TCPClient", "Connecting to server $serverIp:$serverPort")
            socket = Socket(serverIp, serverPort)
            Log.d("TCPClient", "Connected to server!")
            Log.d("TCPClient", "Image URI: $imageUri")

            inputStream = context.contentResolver.openInputStream(imageUri)
            outputStream = socket.getOutputStream()

            if (inputStream != null) {
                Log.d("TCPClient", "Sending image data...")
                val buffer = ByteArray(4096)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                outputStream.flush()
                Log.d("TCPClient", "Image sent successfully.")
            } else {
                Log.e("TCPClient", "Could not open InputStream for URI: $imageUri")
            }
        } catch (e: Exception) {
            Log.e("TCPClient", "Error sending image: ${e.message}", e)
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
                socket?.close()
            } catch (e: IOException) {
                Log.e("TCPClient", "Error closing resources: ${e.message}", e)
            }
        }
    }

}
class FileOperation(private val context: Context){


    fun createFolderInDownloads(folderName: String): File? {

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        val folder = File(downloadsDir, folderName)

        if (!folder.exists()) {
            val created = folder.mkdirs()
            if (!created) {
                return null
            }
        }

        return folder
    }
    fun insertFile( path:String,socket: Socket) {
        var inputStream: InputStream? = null
        var fileStream: FileOutputStream? = null

        try {
            inputStream = socket.getInputStream()


            val firstByte = inputStream.read()
            if (firstByte == -1) {
                Log.d("FileOperation", "No data received, skipping file creation.")
                return
            }
            val filename="IMG_${System.currentTimeMillis()}.jpg"
            Log.d("File","$path:$filename")
            val file = File(path, filename)
            fileStream = FileOutputStream(file)
            fileStream.write(firstByte)

            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                fileStream.write(buffer, 0, bytesRead)
            }

            Log.d("FileOperation", "File received and saved successfully.")
            saveMediaStoreImg(context ,file,filename,"Foreground")
            showFileReceivedNotification(context, file.name)

        } catch (e: Exception) {
            Log.e("FileOperation", "Error writing file: ${e.message}", e)
        } finally {
            try {
                fileStream?.flush()
                fileStream?.close()
                inputStream?.close()
            } catch (e: IOException) {
                Log.e("FileOperation", "Error closing streams: ${e.message}", e)
            }
        }
    }

    private fun showFileReceivedNotification(context: Context, fileName: String) {
        val channelId = "file_received_channel"
        val channelName = "File Received"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        Log.d("Notifction","Notification triggering")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Notifies when a file is received"
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
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()



        Log.d("Notifction","Notification triggered")
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun saveMediaStoreImg(
        context: Context,
        file: File,
        filename: String,
        albumName: String = "Foreground"
    ) {
        var fos: OutputStream? = null
        var imageUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        if (filename.endsWith(".png")) "image/png" else "image/jpeg"
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
                        file.inputStream().use { it.copyTo(outputStream) }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    Log.d("Mediastore","ImageUpdated In Mediastore Successfully")
                } ?: throw Exception("MediaStore Uri was null")

            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES + File.separator + albumName
                )
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                val destFile = File(imagesDir, filename)
                file.copyTo(destFile, overwrite = true)

                val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                imageUri = Uri.fromFile(destFile)
                mediaScanIntent.data = imageUri
                context.sendBroadcast(mediaScanIntent)

                Toast.makeText(context, "Image saved to Gallery: $albumName", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (imageUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.delete(imageUri, null, null)
            }
            Toast.makeText(context, "Error saving image: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        } finally {
            fos?.close()
        }
    }

}









