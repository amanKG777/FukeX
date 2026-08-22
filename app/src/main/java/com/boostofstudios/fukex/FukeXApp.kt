package com.boostofstudios.fukex

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe

class FukeXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            org.schabi.newpipe.extractor.NewPipe.init(YoutubeDownloader.getInstance())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
