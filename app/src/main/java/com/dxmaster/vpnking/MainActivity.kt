package com.dxmaster.vpnking

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder.AudioSource
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private val BG = 0xFF05060F.toInt()
    private val CY = 0xFF00F0FF.toInt()
    private val MG = 0xFFFF2BD6.toInt()
    private val TX = 0xFFDDE6FF.toInt()
    private lateinit var mic: MicView
    private lateinit var root: LinearLayout

    private val rates = intArrayOf(8000, 16000, 22050, 44100, 48000)
    private val srcNames = arrayOf("Mic", "Cam", "Call", "Voice", "Raw")
    private val srcIds = intArrayOf(AudioSource.MIC, AudioSource.CAMCORDER,
        AudioSource.VOICE_COMMUNICATION, AudioSource.VOICE_RECOGNITION, AudioSource.UNPROCESSED)
    private val outNames = arrayOf("Auto", "Speaker", "Headset", "Bluetooth")
    private val skinNames = arrayOf("Cyber", "Pink", "Matrix", "Gold")
    private val skins = arrayOf(
        intArrayOf(0xFF00F0FF.toInt(), 0xFFFF2BD6.toInt()),
        intArrayOf(0xFFFF2BD6.toInt(), 0xFF7A5CFF.toInt()),
        intArrayOf(0xFF39FF14.toInt(), 0xFF00F0FF.toInt()),
        intArrayOf(0xFFFFC400.toInt(), 0xFFFF5A1F.toInt()))
    private var ri = 1; private var si = 0; private var oi = 0; private var ki = 0
    private var jackFocus = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = BG; window.navigationBarColor = BG
        val sv = ScrollView(this).apply { setBackgroundColor(BG) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(36), dp(16), dp(32)) }
        sv.addView(root); setContentView(sv)

        root.addView(TextView(this).apply {
            text = "DX MASTER VPN KING"; setTextColor(CY); textSize = 20f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; letterSpacing = 0.12f
            setShadowLayer(16f, 0f, 0f, CY)
        })
        mic = MicView(this)
        mic.layoutParams = LinearLayout.LayoutParams(-1, dp(340))
        mic.setOnClickListener { toggle() }
        root.addView(mic)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(chip({ "Rate\n${rates[ri]}" }) { ri = (ri + 1) % rates.size; MicEngine.sampleRate = rates[ri]; restart() })
        row.addView(chip({ "Input\n${srcNames[si]}" }) { si = (si + 1) % srcIds.size; MicEngine.inputSource = srcIds[si]; restart() })
        row.addView(chip({ "Output\n${outNames[oi]}" }) { oi = (oi + 1) % outNames.size; MicEngine.output = oi; restart() })
        row.addView(chip({ "Skin\n${skinNames[ki]}" }) { ki = (ki + 1) % skins.size; mic.c1 = skins[ki][0]; mic.c2 = skins[ki][1] })
        root.addView(row)

        header("MIC")
        slider("Mic Gain", 80, 15, { "x${it / 10f}" }) { MicEngine.gain = it / 10f }
        slider("Noise Gate", 100, 0, { if (it == 0) "OFF" else "$it" }) { MicEngine.gate = it / 1000f }
        slider("Reverb", 100, 0, { "$it%" }) { MicEngine.reverb = it / 100f }

        header("EQUALIZER (dB)")
        val names = arrayOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
        for (k in 0..4) slider(names[k], 24, 12, { "${it - 12} dB" }) {
            MicEngine.eqDb[k] = (it - 12).toFloat(); MicEngine.eqDirty = true
        }

        header("EFFECTS")
        sw("Robot Voice", false) { MicEngine.robot = it }
        sw("Echo", false) { MicEngine.echo = it }
        sw("Limiter / Anti-Clip", true) { MicEngine.limiter = it }

        header("AUDIO FOCUS")
        sw("Call Focus - stop during calls (keep OFF for Telegram call)", false) { MicEngine.callFocus = it }
        sw("Media Focus - stop during media playback", false) { MicEngine.mediaFocus = it }
        sw("Jack Focus - start only if headset plugged in", false) { jackFocus = it }
    }

    private fun chip(label: () -> String, click: () -> Unit) = TextView(this).apply {
        text = label(); setTextColor(CY); textSize = 12f; gravity = Gravity.CENTER
        setPadding(dp(4), dp(10), dp(4), dp(10))
        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setStroke(dp(1), CY); setColor(0xFF0B1024.toInt()) }
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
        setOnClickListener { click(); text = label() }
    }

    private fun header(t: String) = root.addView(TextView(this).apply {
        text = t; setTextColor(MG); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.1f; setPadding(0, dp(22), 0, dp(4))
    })

    private fun slider(name: String, max: Int, init: Int, fmt: (Int) -> String, on: (Int) -> Unit) {
        val tv = TextView(this).apply { setTextColor(TX); textSize = 14f; text = "$name: ${fmt(init)}" }
        val sb = SeekBar(this).apply {
            this.max = max; progress = init
            progressTintList = ColorStateList.valueOf(CY); thumbTintList = ColorStateList.valueOf(MG)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { tv.text = "$name: ${fmt(p)}"; on(p) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(tv); root.addView(sb)
    }

    private fun sw(name: String, init: Boolean, on: (Boolean) -> Unit) = root.addView(Switch(this).apply {
        text = name; setTextColor(TX); isChecked = init; setPadding(0, dp(8), 0, dp(8))
        setOnCheckedChangeListener { _, c -> on(c) }
    })

    private fun restart() { if (MicEngine.running) { MicEngine.stop(); MicEngine.start(this) } }

    private fun hasWired(): Boolean {
        val am = getSystemService(AudioManager::class.java)
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    private fun toggle() {
        if (MicEngine.running) {
            startService(Intent(this, MicService::class.java).setAction("STOP")); return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, "android.permission.POST_NOTIFICATIONS"), 1); return
        }
        if (jackFocus && !hasWired()) { Toast.makeText(this, "Plug in headset first", Toast.LENGTH_SHORT).show(); return }
        startForegroundService(Intent(this, MicService::class.java))
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (r.isNotEmpty() && r[0] == PackageManager.PERMISSION_GRANTED) toggle()
    }
}
