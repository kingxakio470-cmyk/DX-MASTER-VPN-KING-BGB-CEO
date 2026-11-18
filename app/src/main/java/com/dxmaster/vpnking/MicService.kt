package com.dxmaster.vpnking

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder

class MicService : Service() {
    private var req: AudioFocusRequest? = null
    private var paused = false
    private val am by lazy { getSystemService(AudioManager::class.java) }
    private val listener = AudioManager.OnAudioFocusChangeListener { ch ->
        if (ch == AudioManager.AUDIOFOCUS_GAIN) {
            if (paused) { paused = false; MicEngine.start(this) }
        } else if ((MicEngine.callFocus || MicEngine.mediaFocus) && MicEngine.running) {
            paused = true; MicEngine.stop()
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "STOP") {
            MicEngine.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mic", "DX MASTER", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "mic")
            .setContentTitle("DX MASTER VPN KING")
            .setContentText("Mic is live")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
        if (MicEngine.callFocus || MicEngine.mediaFocus) {
            req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setOnAudioFocusChangeListener(listener).build()
            am.requestAudioFocus(req!!)
        }
        MicEngine.start(this)
        return START_STICKY
    }

    override fun onDestroy() {
        req?.let { am.abandonAudioFocusRequest(it) }
        MicEngine.stop()
        super.onDestroy()
    }
}
