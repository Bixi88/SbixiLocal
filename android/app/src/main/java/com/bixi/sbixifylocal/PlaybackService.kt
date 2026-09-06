package com.bixi.sbixifylocal

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

// Servizio "vuoto" apposta: il suo unico scopo è tenere il processo dell'app vivo e
// in primo piano durante l'ascolto (Android non sospende un processo con un servizio
// foreground attivo, a differenza di una semplice pagina web in background). Tutta la
// logica vera - MediaSession, notifica, risposta ai tasti - vive in MediaControlsPlugin,
// che tiene un riferimento a questo servizio per aggiornarne la notifica.
class PlaybackService : Service() {

    companion object {
        const val NOTIF_ID = 1001
        var instance: PlaybackService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            val session = MediaControlsPlugin.instance?.getMediaSession()
            if (session != null) {
                androidx.media.session.MediaButtonReceiver.handleIntent(session, intent)
            }
            return START_NOT_STICKY
        }

        val notification = MediaControlsPlugin.instance?.buildCurrentNotification()
        if (notification != null) {
            startForeground(NOTIF_ID, notification)
        } else {
            // Avviato senza che ci sia ancora nulla da mostrare (non dovrebbe succedere
            // nell'uso normale): niente da tenere in vita, ci si ferma subito.
            stopSelf()
        }
        return START_NOT_STICKY
    }

    // Richiamato da MediaControlsPlugin quando cambia brano/stato: aggiorna la stessa
    // notifica già mostrata, senza bisogno di richiamare startForeground() ogni volta.
    fun updateNotification(notification: Notification) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification)
    }

    fun stopPlaybackService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
