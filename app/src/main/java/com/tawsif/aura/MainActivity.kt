package com.tawsif.aura

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var supported: Boolean? = null

    // palette
    private val cBg = Color.parseColor("#0F0F14")
    private val cCard = Color.parseColor("#1B1B23")
    private val cChip = Color.parseColor("#2A2A35")
    private val cSub = Color.parseColor("#9A9AA8")
    private val cAccent = Color.parseColor("#7C5CFF")
    private val cGreen = Color.parseColor("#34C759")
    private val cOrange = Color.parseColor("#FF9F0A")
    private val cRed = Color.parseColor("#FF453A")
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val match = ViewGroup.LayoutParams.MATCH_PARENT

    private val swatches = listOf(
        "#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#00C7BE", "#00E5FF",
        "#007AFF", "#5856D6", "#AF52DE", "#FF2D95", "#FFFFFF", "#FFD9A0"
    ).map { Color.parseColor(it) }

    // effect model: one color; speed 0 = steady light, 1..100 = breathing slow..fast
    private var color = Color.parseColor("#FF3B30")
    private var durSec = 10
    private var forever = false
    private var speed = 50
    private var bright = 100
    private var updatingHex = false

    // widgets
    private lateinit var status: TextView
    private lateinit var preview: PhonePreview
    private lateinit var colorDot: View
    private lateinit var plane: ColorPlane
    private lateinit var hexField: EditText
    private lateinit var durLabel: TextView
    private lateinit var durSeek: SeekBar
    private lateinit var foreverBox: CheckBox
    private lateinit var speedLabel: TextView
    private lateinit var speedSeek: SeekBar
    private lateinit var brightLabel: TextView
    private lateinit var brightSeek: SeekBar
    private lateinit var msg: TextView
    private lateinit var presetBox: LinearLayout
    private var pulse: ObjectAnimator? = null
    private val doneRunnable = Runnable { stopPreview(); say("Done") }

    private val prefs by lazy { getSharedPreferences("aura", MODE_PRIVATE) }

    // ---------- small UI helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun circle(color: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color); setStroke(dp(3), stroke)
    }
    private fun text(t: String, size: Float = 14f, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    private fun card(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = rounded(cCard, 20)
        v.forEach { addView(it) }
        layoutParams = LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(12) }
    }
    private fun title(t: String) = text(t, 16f, Color.WHITE, true).apply { setPadding(0, 0, 0, dp(8)) }
    private fun pill(label: String, bg: Int, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f; setTextColor(Color.WHITE)
        background = rounded(bg, 16); stateListAnimator = null
        setOnClickListener { onClick() }
    }
    private fun twoCols(a: View, b: View, gap: Int = 8) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(a, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(gap / 2) })
        addView(b, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(gap / 2) })
    }
    private fun labelRow(name: String, value: TextView) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(10), 0, 0)
        addView(text(name, 14f, cSub), LinearLayout.LayoutParams(0, wrap, 1f))
        addView(value)
    }
    private fun seek(max: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        this.max = max
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { onChange(p) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }
    private fun say(s: String) = runOnUiThread { msg.text = s }

    // ---------- screen ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(20), dp(16), dp(40))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(cBg); addView(root) })

        // header (status is informational only)
        status = text("", 13f, Color.WHITE, true).apply { setPadding(dp(14), dp(7), dp(14), dp(7)) }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(text("Aura Light", 28f, Color.WHITE, true), LinearLayout.LayoutParams(0, wrap, 1f))
            addView(status)
        })

        // preview: rear of the iQOO Z10x / vivo T4x
        preview = PhonePreview(this).apply { fadeColor = cBg }
        root.addView(preview, LinearLayout.LayoutParams(match, dp(210)).apply { topMargin = dp(8) })
        root.addView(text("iQOO Z10x / vivo T4x, rear view", 12f, cSub).apply { gravity = Gravity.CENTER })

        // color
        colorDot = View(this)
        hexField = EditText(this).apply {
            hint = "RRGGBB"; typeface = Typeface.MONOSPACE; textSize = 14f
            setTextColor(Color.WHITE); setHintTextColor(cSub); setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(6))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val t = s?.toString() ?: return
                    if (updatingHex || t.length != 6) return
                    val v = t.toIntOrNull(16) ?: return
                    color = v or (0xFF shl 24)
                    plane.setColor(color); syncUi(skipHex = true)
                }
            })
        }
        val hexRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        hexRow.addView(colorDot, LinearLayout.LayoutParams(dp(40), dp(40)))
        hexRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        hexRow.addView(text("#", 16f, cSub))
        hexRow.addView(hexField, LinearLayout.LayoutParams(dp(100), wrap))

        plane = ColorPlane(this).apply { onPick = { c -> color = c; syncUi() } }
        plane.layoutParams = LinearLayout.LayoutParams(match, dp(170)).apply { topMargin = dp(12); bottomMargin = dp(12) }

        val swatchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        swatches.forEach { c ->
            swatchRow.addView(View(this).apply {
                background = circle(c, Color.parseColor("#33FFFFFF"))
                setOnClickListener { color = c; plane.setColor(c); syncUi() }
            }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) })
        }
        val swatchScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; addView(swatchRow)
        }
        root.addView(card(title("Color"), hexRow, plane, swatchScroll))

        // timing
        durLabel = text("", 14f); speedLabel = text("", 14f); brightLabel = text("", 14f)
        durSeek = seek(59) { durSec = it + 1; syncLabels() }
        speedSeek = seek(100) { speed = it; syncLabels() }
        brightSeek = seek(95) { bright = it + 5; syncLabels() }
        foreverBox = CheckBox(this).apply {
            text = "Keep on until I stop it (max 1 hour)"; setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, c -> forever = c; durSeek.isEnabled = !c; syncLabels() }
        }
        root.addView(card(
            title("Timing"),
            labelRow("Duration", durLabel), durSeek, foreverBox,
            labelRow("Speed (0 = steady light)", speedLabel), speedSeek,
            labelRow("Brightness", brightLabel), brightSeek
        ))

        // actions
        msg = text("", 13f, cSub).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        root.addView(twoCols(
            pill("Start", cAccent) { startEffect() },
            pill("Stop", cChip) { stopEffect() }
        ), LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(14) })
        root.addView(msg)

        // presets
        presetBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(card(
            title("My presets"),
            text("Save the current look. Tap one to play it, long-press to delete.", 13f, cSub),
            presetBox,
            pill("Save current as preset", cChip) { savePresetDialog() }.also {
                it.layoutParams = LinearLayout.LayoutParams(match, dp(48)).apply { topMargin = dp(10) }
            }
        ))

        // restore last used look
        prefs.getString("last", null)?.let { try { applyState(JSONObject(it)) } catch (_: Throwable) {} }
        plane.setColor(color)
        syncUi()
        renderPresets()
        refreshStatus()
    }

    override fun onResume() { super.onResume(); if (supported == null) probe() }

    override fun onDestroy() {
        pulse?.cancel()
        super.onDestroy()
    }

    // ---------- state -> UI ----------
    private fun syncUi(skipHex: Boolean = false) {
        colorDot.background = circle(color, Color.WHITE)
        preview.ringColor = color
        if (!skipHex) {
            updatingHex = true
            hexField.setText(String.format("%06X", color and 0xFFFFFF))
            updatingHex = false
        }
        durSeek.progress = durSec - 1; speedSeek.progress = speed; brightSeek.progress = bright - 5
        foreverBox.isChecked = forever; durSeek.isEnabled = !forever
        syncLabels()
    }

    private fun syncLabels() {
        durLabel.text = if (forever) "Until stopped" else "$durSec s"
        speedLabel.text = if (speed == 0) "Steady" else if (speed < 34) "Slow" else if (speed < 67) "Medium" else "Fast"
        brightLabel.text = "$bright%"
    }

    private fun refreshStatus() = runOnUiThread {
        val (label, c) = when (supported) {
            true -> "Ready" to cGreen
            false -> "Not supported" to cRed
            null -> "Checking…" to cOrange
        }
        status.text = "●  $label"
        status.background = rounded((c and 0x00FFFFFF) or (0x33 shl 24), 20)
        status.setTextColor(c)
    }

    /** Harmless call to see whether this phone has the vivo / iQOO back light. */
    private fun probe() {
        io.execute {
            val r = LightCore.call("info", packageName, "")
            // Only a real failure (class missing / call rejected) counts as unsupported.
            supported = !r.startsWith("ERROR")
            if (supported == false) say("This app works on vivo / iQOO phones that have a back light.")
            refreshStatus()
        }
    }

    // ---------- effect building ----------
    private fun hex(c: Int): String {
        val v = if (AuraConfig.SWAP_RED_BLUE) ((c and 0xFF) shl 16) or (c and 0xFF00) or ((c shr 16) and 0xFF) else c
        return String.format("%06X", v and 0xFFFFFF)
    }

    /** Milliseconds per breathing cycle; only used when speed > 0. */
    private fun period() = 4000 - (speed - 1) * 3500 / 99

    private fun buildJson(): String {
        val total = if (forever) 3_600_000 else durSec * 1000
        val e = JSONObject()
        e.put("type", "breathing")
        e.put("lightName", AuraConfig.LIGHT_NAME)
        e.put("brightness", bright * 255 / 100)
        e.put("isCountPriority", false)
        e.put("totalTime", total)
        e.put("lightColor", hex(color))
        if (speed == 0) {
            // Steady: fade in quickly, then just hold the light on for the whole duration.
            val rise = 150; val fall = 1; val close = 1
            e.put("periodTime", total); e.put("count", 1)
            e.put("riseTime", rise); e.put("holdTime", total - rise - fall - close)
            e.put("fallTime", fall); e.put("closeTime", close)
        } else {
            val p = period()
            val rise = p * 35 / 100; val hold = p * 15 / 100
            e.put("periodTime", p); e.put("count", maxOf(1, (total + p - 1) / p))
            e.put("riseTime", rise); e.put("holdTime", hold); e.put("fallTime", rise)
            e.put("closeTime", p - rise * 2 - hold)
        }
        return JSONObject().put("combineName", "aura_custom").put("effects", JSONArray().put(e)).toString()
    }

    private fun startEffect() {
        val json = buildJson()
        prefs.edit().putString("last", stateJson().toString()).apply()
        say("Starting…")
        startPreview()
        io.execute {
            LightCore.call("stopall", packageName, "")
            val r = LightCore.call("json", packageName, json)
            val id = r.substringAfter("-> ", "").trim().toIntOrNull()
            if (id != null && id >= 0) say("Playing ✓") else {
                runOnUiThread { stopPreview() }
                say("Couldn't start the light. This app works on vivo / iQOO phones with a back light.")
            }
        }
    }

    private fun stopEffect() {
        stopPreview()
        io.execute { LightCore.call("stopall", packageName, ""); say("Stopped") }
    }

    private fun startPreview() {
        stopPreview()
        if (speed > 0) {
            val a = ObjectAnimator.ofFloat(preview, "glow", 1f, 0.12f)
            a.duration = (period() / 2).toLong().coerceAtLeast(250L)
            a.repeatMode = ValueAnimator.REVERSE
            a.repeatCount = ValueAnimator.INFINITE
            a.start(); pulse = a
        }
        if (!forever) preview.postDelayed(doneRunnable, durSec * 1000L)
    }

    private fun stopPreview() {
        preview.removeCallbacks(doneRunnable)
        pulse?.cancel(); pulse = null
        preview.glow = 1f
    }

    // ---------- presets ----------
    private fun stateJson() = JSONObject()
        .put("color", color).put("dur", durSec).put("forever", forever)
        .put("speed", speed).put("bright", bright)

    /** Older saves stored a list of colors; the first one is the one that still applies. */
    private fun colorOf(o: JSONObject): Int {
        if (o.has("color")) return o.getInt("color")
        val a = o.optJSONArray("colors")
        return if (a != null && a.length() > 0) a.getInt(0) else Color.WHITE
    }

    private fun applyState(o: JSONObject) {
        color = colorOf(o)
        durSec = o.optInt("dur", 10).coerceIn(1, 60)
        forever = o.optBoolean("forever", false)
        speed = o.optInt("speed", 50).coerceIn(0, 100)
        bright = o.optInt("bright", 100).coerceIn(5, 100)
        if (this::plane.isInitialized) plane.setColor(color)
        if (this::hexField.isInitialized) syncUi()
    }

    private fun loadPresets(): JSONArray = try { JSONArray(prefs.getString("presets", "[]")) } catch (_: Throwable) { JSONArray() }

    private fun savePresetDialog() {
        val input = EditText(this).apply { hint = "Preset name"; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Save preset").setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Preset" }
                val list = loadPresets()
                list.put(stateJson().put("name", name))
                prefs.edit().putString("presets", list.toString()).apply()
                renderPresets()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun renderPresets() {
        presetBox.removeAllViews()
        val list = loadPresets()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val rowV = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10)); background = rounded(cChip, 14)
                addView(View(context).apply { background = circle(colorOf(o), Color.parseColor("#44FFFFFF")) },
                    LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(12) })
                addView(text(o.optString("name", "Preset"), 15f), LinearLayout.LayoutParams(0, wrap, 1f))
                addView(text((if (o.optInt("speed", 50) == 0) "Steady" else "Breathing") + " ▶", 13f, cSub))
                setOnClickListener { applyState(o); startEffect() }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle("Delete preset?")
                        .setPositiveButton("Delete") { _, _ ->
                            list.remove(i); prefs.edit().putString("presets", list.toString()).apply(); renderPresets()
                        }.setNegativeButton("Cancel", null).show()
                    true
                }
            }
            presetBox.addView(rowV, LinearLayout.LayoutParams(match, wrap).apply { topMargin = dp(8) })
        }
    }
}
