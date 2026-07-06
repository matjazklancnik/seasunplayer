package com.example.shazamytdl

import android.app.Application
import android.util.Log
import com.example.shazamytdl.download.DownloadQueueManager
import com.example.shazamytdl.download.YoutubeDlBridge
import com.example.shazamytdl.player.PlayerHolder

class ShazamYtdlApp : Application() {
    lateinit var playerHolder: PlayerHolder
        private set

    override fun onCreate() {
        super.onCreate()
        playerHolder = PlayerHolder(this)
        try {
            YoutubeDlBridge.init(this)
            DownloadQueueManager.init(this)
        } catch (t: Throwable) {
            Log.e("ShazamYtdlApp", "youtubedl-android init failed; download can retry later", t)
        }
    }
}
