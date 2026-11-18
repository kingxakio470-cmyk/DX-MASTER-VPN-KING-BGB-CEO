package com.dxmaster.vpnking

import android.content.Context
import android.media.*
import kotlin.math.*

object MicEngine {
    @Volatile var running = false
    @Volatile var gain = 1.5f
    @Volatile var gate = 0f
    @Volatile var reverb = 0f
    @Volatile var robot = false
    @Volatile var echo = false
    @Volatile var limiter = true
    @Volatile var level = 0f
    @Volatile var sampleRate = 16000
    @Volatile var inputSource = MediaRecorder.AudioSource.MIC
    @Volatile var output = 0
    @Volatile var callFocus = false
    @Volatile var mediaFocus = false
    val eqDb = FloatArray(5)
    @Volatile var eqDirty = true
    private var thread: Thread? = null
    private val freqs = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)

    fun start(ctx: Context) {
        if (running) return
        running = true
        val app = ctx.applicationContext
        val t = Thread { loop(app) }
        thread = t
        t.priority = Thread.MAX_PRIORITY
        t.start()
    }

    fun stop() {
        running = false
        try { thread?.join(800) } catch (_: Exception) {}
        thread = null
        level = 0f
    }

    private fun route(ctx: Context, track: AudioTrack) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val want = when (output) {
            1 -> listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            2 -> listOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET)
            3 -> listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            else -> emptyList()
        }
        if (want.isEmpty()) return
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in want }?.let { track.preferredDevice = it }
    }

    private fun loop(ctx: Context) {
        val rate = sampleRate
        val fmtIn = AudioFormat.CHANNEL_IN_MONO
        val minIn = AudioRecord.getMinBufferSize(rate, fmtIn, AudioFormat.ENCODING_PCM_16BIT)
        val minOut = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minIn <= 0 || minOut <= 0) { running = false; return }
        val bytes = max(minIn, minOut)
        var rec: AudioRecord? = null
        var track: AudioTrack? = null
        try {
            val r = AudioRecord(inputSource, rate, fmtIn, AudioFormat.ENCODING_PCM_16BIT, bytes * 2)
            rec = r
            if (r.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("mic init failed")
            val usage = if (inputSource == MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(bytes * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            track = t
            route(ctx, t)

            val n = 512
            val buf = ShortArray(n)
            val b0 = FloatArray(5); val b1 = FloatArray(5); val b2 = FloatArray(5)
            val a1 = FloatArray(5); val a2 = FloatArray(5)
            val z1 = FloatArray(5); val z2 = FloatArray(5)
            val combMs = floatArrayOf(29.7f, 37.1f, 41.1f, 43.7f)
            val cb = Array(4) { FloatArray((combMs[it] * rate / 1000f).toInt()) }
            val ci = IntArray(4)
            val eb = FloatArray(rate * 3 / 10); var ei = 0
            var env = 0f; var gg = 1f; var ph = 0f
            val phInc = (2.0 * PI * 70.0 / rate).toFloat()
            val twoPi = (2.0 * PI).toFloat()

            r.startRecording(); t.play()
            while (running) {
                val cnt = r.read(buf, 0, n)
                if (cnt < 0) break
                if (cnt == 0) continue
                if (eqDirty) {
                    eqDirty = false
                    for (k in 0..4) {
                        val f = freqs[k]
                        if (f >= rate * 0.45f || eqDb[k] == 0f) {
                            b0[k] = 1f; b1[k] = 0f; b2[k] = 0f; a1[k] = 0f; a2[k] = 0f
                        } else {
                            val A = 10.0.pow(eqDb[k] / 40.0)
                            val w0 = 2.0 * PI * f / rate
                            val al = sin(w0) / 2.0
                            val c = cos(w0)
                            val a0 = 1 + al / A
                            b0[k] = ((1 + al * A) / a0).toFloat()
                            b1[k] = ((-2 * c) / a0).toFloat()
                            b2[k] = ((1 - al * A) / a0).toFloat()
                            a1[k] = ((-2 * c) / a0).toFloat()
                            a2[k] = ((1 - al / A) / a0).toFloat()
                        }
                    }
                }
                val g = gain; val gt = gate; val rv = reverb
                val rb = robot; val ec = echo; val lm = limiter
                var pk = 0f
                for (i in 0 until cnt) {
                    var x = buf[i] / 32768f * g
                    val ax = abs(x)
                    env = if (ax > env) ax else env * 0.9995f
                    gg += ((if (gt > 0f && env < gt) 0f else 1f) - gg) * 0.02f
                    x *= gg
                    for (k in 0..4) {
                        val y = b0[k] * x + z1[k]
                        z1[k] = b1[k] * x - a1[k] * y + z2[k]
                        z2[k] = b2[k] * x - a2[k] * y
                        x = y
                    }
                    if (rb) { x *= (0.35f + 0.65f * sin(ph)); ph += phInc; if (ph > twoPi) ph -= twoPi }
                    if (ec) { val d = eb[ei]; eb[ei] = x + d * 0.4f; x += d * 0.5f; ei++; if (ei >= eb.size) ei = 0 }
                    if (rv > 0f) {
                        var wet = 0f
                        for (k in 0..3) {
                            val d = cb[k][ci[k]]
                            cb[k][ci[k]] = x + d * 0.8f
                            wet += d
                            ci[k] = (ci[k] + 1) % cb[k].size
                        }
                        x += wet * 0.2f * rv
                    }
                    x = if (lm) tanh(x) else x.coerceIn(-1f, 1f)
                    pk = max(pk, abs(x))
                    buf[i] = (x * 32767f).toInt().toShort()
                }
                level = max(pk, level * 0.8f)
                t.write(buf, 0, cnt)
            }
        } catch (_: Exception) {
        } finally {
            try { rec?.stop() } catch (_: Exception) {}
            rec?.release()
            try { track?.stop() } catch (_: Exception) {}
            track?.release()
            if (thread === Thread.currentThread()) running = false
            level = 0f
        }
    }
}
