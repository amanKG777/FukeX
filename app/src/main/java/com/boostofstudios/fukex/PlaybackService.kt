package com.boostofstudios.fukex
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import com.boostofstudios.fukex.data.PlaylistManager
import com.boostofstudios.fukex.data.SettingsManager
import com.boostofstudios.fukex.playback.EngineState
import com.boostofstudios.fukex.playback.PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// Keeps the process alive while PlaybackEngine plays, shows the media notification,
// and serves the browse tree that Android Auto and other system media UIs use.
class PlaybackService : MediaBrowserServiceCompat() {
    companion object {
        const val CHANNEL_ID = "fukex_playback_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.boostofstudios.fukex.ACTION_PLAY"
        const val ACTION_PAUSE = "com.boostofstudios.fukex.ACTION_PAUSE"
        const val ACTION_NEXT = "com.boostofstudios.fukex.ACTION_NEXT"
        const val ACTION_PREV = "com.boostofstudios.fukex.ACTION_PREV"
        const val ACTION_STOP = "com.boostofstudios.fukex.ACTION_STOP"
        const val ACTION_VOICE_ASSISTANT = "com.boostofstudios.fukex.ACTION_VOICE_ASSISTANT"
        private const val ROOT_ID = "root"
        private const val EMPTY_ROOT_ID = "empty"

        // Packages allowed to browse playlists: Android Auto, Wear OS and the system media UI
        private val TRUSTED_BROWSERS = setOf(
            "com.google.android.projection.gearhead",
            "com.google.android.wearable.app",
            "com.google.android.apps.wearable.phone",
            "com.android.systemui",
            "com.google.android.googlequicksearchbox"
        )

        @Volatile var isRunning = false
            private set
    }

    private lateinit var engine: PlaybackEngine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sensorManager: android.hardware.SensorManager? = null
    private var shakeDetector: ShakeDetector? = null
    private var isForeground = false

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "voice_assistant_enabled", "voice_assistant_background",
            SettingsManager.KEY_VOICE_SHAKE_ENABLED, SettingsManager.KEY_VOICE_SHAKE_SENSITIVITY -> updateShakeDetector()
        }
    }

    // Shaking is optional; hold-Volume-Down in the background is handled by VolumeKeyAccessibilityService
    private fun updateShakeDetector() {
        val wanted = SettingsManager.isVoiceAssistantEnabled(this) &&
            SettingsManager.isVoiceAssistantBackgroundEnabled(this) &&
            SettingsManager.isVoiceShakeEnabled(this)
        if (!wanted) {
            shakeDetector?.let { sensorManager?.unregisterListener(it) }
            return
        }
        val threshold = SettingsManager.shakeThresholdFor(SettingsManager.getVoiceShakeSensitivity(this))
        if (sensorManager == null) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        }
        val detector = shakeDetector ?: ShakeDetector(threshold) {
            // MainActivity's assistant gives the buzz/beep feedback once it starts listening
            VoiceAssistantTrigger.request(this)
        }.also { shakeDetector = it }
        detector.thresholdG = threshold
        val accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return
        // Unregister first so repeated calls don't stack listeners
        sensorManager?.unregisterListener(detector)
        sensorManager?.registerListener(detector, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        engine = PlaybackEngine.get(this)
        sessionToken = engine.session.sessionToken
        createNotificationChannel()
        SettingsManager.registerChangeListener(this, prefsListener)
        updateShakeDetector()
        scope.launch {
            engine.state
                .distinctUntilChanged { a, b -> a.isPlaying == b.isPlaying && a.currentTitle == b.currentTitle && a.queueName == b.queueName && a.queue.isEmpty() == b.queue.isEmpty() }
                .collect { updateNotification(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A foreground start must be answered with startForeground, even when there's nothing to show
        goForeground(buildNotification(engine.state.value))
        when (intent?.action) {
            Intent.ACTION_MEDIA_BUTTON -> androidx.media.session.MediaButtonReceiver.handleIntent(engine.session, intent)
            ACTION_PLAY -> engine.play()
            ACTION_PAUSE -> engine.pause()
            ACTION_NEXT -> engine.next()
            ACTION_PREV -> engine.previous()
            ACTION_STOP -> {
                engine.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
                stopSelf()
                return START_NOT_STICKY
            }
        }
        updateNotification(engine.state.value)
        return START_NOT_STICKY
    }

    private fun updateNotification(state: EngineState) {
        if (state.queue.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
            return
        }
        val notification = buildNotification(state)
        if (state.isPlaying) {
            goForeground(notification)
        } else {
            // Paused: keep the notification so playback can be resumed, but let the system reclaim the service
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_DETACH)
                isForeground = false
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun goForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
        } catch (e: Exception) {
            // Android 12+ can refuse this from the background; the notification still updates
            android.util.Log.w("PlaybackService", "Could not enter foreground", e)
        }
    }

    private fun buildNotification(state: EngineState): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingMainIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(state.currentTitle.ifEmpty { "FukeX" })
            .setContentText(state.queueName)
            .setContentIntent(pendingMainIntent)
            .setDeleteIntent(PendingIntent.getService(this, 4, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(state.isPlaying)
        builder.addAction(
            android.R.drawable.ic_media_previous, "Previous",
            PendingIntent.getService(this, 1, Intent(this, PlaybackService::class.java).setAction(ACTION_PREV), PendingIntent.FLAG_IMMUTABLE)
        )
        if (state.isPlaying) {
            builder.addAction(
                android.R.drawable.ic_media_pause, "Pause",
                PendingIntent.getService(this, 2, Intent(this, PlaybackService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_play, "Play",
                PendingIntent.getService(this, 2, Intent(this, PlaybackService::class.java).setAction(ACTION_PLAY), PendingIntent.FLAG_IMMUTABLE)
            )
        }
        builder.addAction(
            android.R.drawable.ic_media_next, "Next",
            PendingIntent.getService(this, 3, Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE)
        )
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(engine.session.sessionToken)
        )
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FukeX Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for background playback"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        val trusted = clientPackageName == packageName ||
            clientUid == Process.SYSTEM_UID ||
            clientPackageName in TRUSTED_BROWSERS
        // Untrusted apps may still control playback, but can't list playlist contents
        return BrowserRoot(if (trusted) ROOT_ID else EMPTY_ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if (parentId == EMPTY_ROOT_ID) {
            result.sendResult(mutableListOf())
            return
        }
        result.detach()
        scope.launch {
            val playlists = kotlinx.coroutines.withContext(Dispatchers.IO) {
                PlaylistManager.loadPlaylists(this@PlaybackService).filter { !it.isHidden }
            }
            val items = if (parentId == ROOT_ID) {
                playlists.map { p ->
                    MediaBrowserCompat.MediaItem(
                        MediaDescriptionCompat.Builder()
                            .setMediaId("playlist/${p.id}")
                            .setTitle(p.name)
                            .setSubtitle("${p.uris.size} tracks")
                            .build(),
                        MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                    )
                }
            } else {
                val playlist = playlists.firstOrNull { "playlist/${it.id}" == parentId }
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    playlist?.uris?.mapIndexed { i, uri ->
                        MediaBrowserCompat.MediaItem(
                            MediaDescriptionCompat.Builder()
                                .setMediaId("playlist/${playlist.id}/$i")
                                .setTitle(trackTitle(uri, i, this@PlaybackService))
                                .setSubtitle(playlist.name)
                                .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                    } ?: emptyList()
                }
            }
            result.sendResult(items.toMutableList())
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!engine.state.value.isPlaying) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        SettingsManager.unregisterChangeListener(this, prefsListener)
        shakeDetector?.let { sensorManager?.unregisterListener(it) }
        scope.cancel()
        super.onDestroy()
    }
}
