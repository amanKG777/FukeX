package com.boostofstudios.fukex.data
import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object PlayerCacheManager {
    private var cache: SimpleCache? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun getCache(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: run {
                val cacheDir = File(context.cacheDir, "media_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(1024L * 1024L * 500L) // 500 MB limit
                val databaseProvider = StandaloneDatabaseProvider(context)
                val newCache = SimpleCache(cacheDir, evictor, databaseProvider)
                cache = newCache
                newCache
            }
        }
    }
}
