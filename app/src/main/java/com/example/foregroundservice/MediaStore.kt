package com.example.foregroundservice

import android.content.ContentValues
import android.net.Uri
import  android.content.Context
import  android.provider.MediaStore
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import  android.util.Log
class MediaStoreChanges(private  val context: Context,private val onMediachange:(Uri?)->Unit) {
    private  val handelerthread= HandlerThread("Mediastore").apply { start() }
    private val contentObserver=object : ContentObserver(Handler(handelerthread.looper)){
        override fun onChange(selfChange: Boolean,uri:Uri?) {
            super.onChange(selfChange,uri)


            onMediachange(uri)

        }
    }
    fun register(){
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
        Log.d("MediaStore","Observer registered")
    }

    fun unregister(){
        context.contentResolver.unregisterContentObserver(contentObserver)
        Log.d("MediaStore","observer Unregistered")
    }

}