package com.bixi.sbixifylocal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

// Fa da ponte fra lo stato di riproduzione (gestito in JavaScript, dentro la WebView) e
// una vera notifica media Android con MediaSession: senza questo, a schermo spento
// Android sospende la pagina web e la riproduzione si ferma dopo pochi secondi. La
// notifica in stile media (copertina, titolo, tasti prec/play/succ) e i tasti
// fisici/cuffie/auto passano da qui, che poi rimanda l'azione a JS tramite un evento.
@CapacitorPlugin(name = "MediaControls")
class MediaControlsPlugin : Plugin() {

    companion object {
        const val CHANNEL_ID = "sbixilocal_playback"
        var instance: MediaControlsPlugin? = null
    }

    private var mediaSession: MediaSessionCompat? = null
    private var currentTitle = "SbixiLocal"
    private var currentArtist = ""
    private var currentArtwork: Bitmap? = null
    private var currentDurationMs = 0L
    private var currentPositionMs = 0L
    private var currentIsPlaying = false
    private var hasActiveTrack = false

    override fun load() {
        instance = this
        createNotificationChannel()
        setupMediaSession()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Riproduzione musica", NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            channel.setSound(null, null)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun setupMediaSession() {
        val session = MediaSessionCompat(context, "SbixiLocalSession")
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { notifyListeners("play", JSObject()) }
            override fun onPause() { notifyListeners("pause", JSObject()) }
            override fun onSkipToNext() { notifyListeners("next", JSObject()) }
            override fun onSkipToPrevious() { notifyListeners("prev", JSObject()) }
            override fun onSeekTo(pos: Long) {
                val data = JSObject()
                data.put("positionMs", pos)
                notifyListeners("seek", data)
            }
            override fun onStop() { notifyListeners("pause", JSObject()) }
        })
        session.isActive = true
        mediaSession = session
    }

    fun getMediaSession(): MediaSessionCompat? = mediaSession

    // Chiamato da JS (in un'unica chiamata, per non dover sincronizzare più stati)
    // ogni volta che cambia qualcosa di rilevante: nuovo brano, play/pausa, avanzamento.
    @PluginMethod
    fun updateNowPlaying(call: PluginCall) {
        currentTitle = call.getString("title") ?: "SbixiLocal"
        currentArtist = call.getString("artist") ?: ""
        currentDurationMs = (call.getInt("durationMs") ?: 0).toLong()
        currentPositionMs = (call.getInt("positionMs") ?: 0).toLong()
        currentIsPlaying = call.getBoolean("isPlaying") ?: false
        hasActiveTrack = true

        val artworkBase64 = call.getString("artworkBase64")
        currentArtwork = if (artworkBase64 != null) {
            try {
                val bytes = Base64.decode(artworkBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) { null }
        } else null

        updateSessionMetadata()
        updateSessionPlaybackState()
        pushNotification()
        call.resolve()
    }

    // Chiamato quando l'app chiude del tutto la riproduzione (playlist svuotata):
    // toglie la notifica e ferma il servizio in primo piano.
    @PluginMethod
    fun clearNowPlaying(call: PluginCall) {
        hasActiveTrack = false
        currentIsPlaying = false
        PlaybackService.instance?.stopPlaybackService()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(PlaybackService.NOTIF_ID)
        call.resolve()
    }

    private fun updateSessionMetadata() {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDurationMs)
        if (currentArtwork != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentArtwork)
        }
        mediaSession?.setMetadata(builder.build())
    }

    private fun updateSessionPlaybackState() {
        val state = if (currentIsPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, currentPositionMs, 1f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    // Avvia il servizio in primo piano se non è già attivo, altrimenti aggiorna
    // semplicemente la notifica già mostrata (niente bisogno di richiamare
    // startForeground ogni volta, solo la prima).
    private fun pushNotification() {
        if (!hasActiveTrack) return
        val running = PlaybackService.instance
        if (running == null) {
            val intent = Intent(context, PlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
        } else {
            running.updateNotification(buildCurrentNotification()!!)
        }
    }

    // Richiamata da PlaybackService quando parte, per avere subito la notifica da
    // mostrare in startForeground(). Ritorna null se non c'è ancora nulla in
    // riproduzione (il servizio, in quel caso, si ferma da solo).
    fun buildCurrentNotification(): Notification? {
        if (!hasActiveTrack) return null

        val playPauseIcon = if (currentIsPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseAction = if (currentIsPlaying) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY

        val openAppIntent = Intent(context, MainActivity::class.java)
        val contentIntent = android.app.PendingIntent.getActivity(
            context, 0, openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(currentIsPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_previous, "Precedente",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context, android.content.ComponentName(context, PlaybackService::class.java), PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            )
            .addAction(
                playPauseIcon, "Play/Pausa",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context, android.content.ComponentName(context, PlaybackService::class.java), playPauseAction
                )
            )
            .addAction(
                android.R.drawable.ic_media_next, "Successivo",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context, android.content.ComponentName(context, PlaybackService::class.java), PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                )
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (currentArtwork != null) builder.setLargeIcon(currentArtwork)

        return builder.build()
    }
}
