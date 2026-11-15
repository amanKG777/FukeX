package com.boostofstudios.fukex

import android.app.Application
import org.schabi.newpipe.extractor.NewPipe

class FukeXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Reads the downloaded icon pack (if any) without holding up startup
        Thread {
            com.boostofstudios.fukex.data.IconPackManager.load(this)
            // An update that was downloaded last time has either been installed or abandoned by now
            com.boostofstudios.fukex.data.UpdateManager.cleanUp(this)
        }.start()
        try {
            org.schabi.newpipe.extractor.NewPipe.init(YoutubeDownloader.getInstance())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
