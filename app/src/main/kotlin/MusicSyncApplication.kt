package io.github.joshkeilder.musicsync

import android.app.Application

class MusicSyncApplication : Application() {
    lateinit var musicSyncAppContainer: MusicSyncAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        musicSyncAppContainer = MusicSyncAppContainer(this)
    }
}
