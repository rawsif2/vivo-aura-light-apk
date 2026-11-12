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

    // effect model
    private val kinds = listOf("breathing", "strobe", "marquee")
    private val kindLabels = listOf("Breathing", "Blink", "Chase")
    private val kindHints = listOf(
        "Smooth fade in and out with one color.",
        "Flashes through up to 4 colors. Use the same color twice to use fewer.",
        "Colors run around the light, up to 4 colors."
    )
    private val swatches = listOf(
        "#FF3B30", "#FF9500", "#FFCC00", "#34C759", "#00C7BE", "#00E5FF",
        "#007AFF", "#5856D6", "#AF52DE", "#FF2D95", "#FFFFFF", "#FFD9A0"
    ).map { Color.parseColor(it) }
    private val colors = intArrayOf(
        Color.parseColor("#FF3B30"), Color.parseColor("#34C759"),
        Color.parseColor("#007AFF"), Color.WHITE
    )
    private var kind = 0
    private var slot = 0
    private var durSec = 10
    private var forever = false
    private var speed = 50      // 0 = steady light, 1..100 = slow..fast
    private var bright = 100
    private var updatingHex = false

    // widgets
    private lateinit var status: TextView
    private lateinit var preview: View
    private lateinit var kindButtons: List<Button>
    private lateinit var kindHint: TextView
    private lateinit var slotViews: List<View>
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

        // preview
        preview = View(this)
        root.addView(preview, LinearLayout.LayoutParams(match, dp(110)).apply { topMargin = dp(16) })

        // effect style
        kindButtons = kindLabels.mapIndexed { i, l ->
            pill(l, cChip) { kind = i; if (kind == 0) slot = 0; syncUi() }
        }
        kindHint = text("", 13f, cSub).apply { setPadding(0, dp(8), 0, 0) }
        val kindRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        kindButtons.forEachIndexed { i, b ->
            kindRow.addView(b, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                if (i > 0) marginStart = dp(8)
            })
        }
        root.addView(card(title("Effect"), kindRow, kindHint))

        // colors
        slotViews = (0 until 4).map { i ->
            View(this).apply { setOnClickListener { slot = i; plane.setColor(colors[i]); syncUi() } }
        }
        val slotRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        slotViews.forEach { slotRow.addView(it, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) }) }
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
                    colors[slot] = v or (0xFF shl 24)
                    plane.setColor(colors[slot]); syncUi(skipHex = true)
                }
            })
        }
        slotRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        slotRow.addView(text("#", 16f, cSub))
        slotRow.addView(hexField, LinearLayout.LayoutParams(dp(100), wrap))

        plane = ColorPlane(this).apply { onPick = { c -> colors[slot] = c; syncUi() } }
        plane.layoutParams = LinearLayout.LayoutParams(match, dp(170)).apply { topMargin = dp(12); bottomMargin = dp(12) }

        val swatchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        swatches.forEach { c ->
            swatchRow.addView(View(this).apply {
                background = circle(c, Color.parseColor("#33FFFFFF"))
                setOnClickListener { colors[slot] = c; plane.setColor(c); syncUi() }
            }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) })
        }
        val swatchScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; addView(swatchRow)
        }
        root.addView(card(title("Color"), slotRow, plane, swatchScroll))

        // timing
        durLabel = text("", 14f); speedLabel = text("", 14f); brightLabel = text("", 14f)
        durSeek = seek(59) { durSec = it + 1; syncLabels() }
        speedSeek = seek(100) { if (speed != it) { speed = it; syncUi() } }
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
        plane.setColor(colors[slot])
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
        kindButtons.forEachIndexed { i, b -> b.background = rounded(if (i == kind) cAccent else cChip, 14) }
        kindHint.text = if (speed == 0) "Steady light, no blinking. Uses the first color." else kindHints[kind]
        val used = if (kind == 0 || speed == 0) 1 else 4
        if (slot >= used) slot = 0
        for (i in 0 until 4) {
            slotViews[i].visibility = if (i < used) View.VISIBLE else View.GONE
            slotViews[i].background = circle(colors[i], if (i == slot) Color.WHITE else Color.parseColor("#44FFFFFF"))
        }
        val shown = (0 until used).map { colors[it] }.let { if (it.size == 1) listOf(it[0], it[0]) else it }
        preview.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, shown.toIntArray())
            .apply { cornerRadius = dp(22).toFloat() }
        if (!skipHex) {
            updatingHex = true
            hexField.setText(String.format("%06X", colors[slot] and 0xFFFFFF))
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
        val (label, color) = when (supported) {
            true -> "Ready" to cGreen
            false -> "Not supported" to cRed
            null -> "Checking…" to cOrange
        }
        status.text = "●  $label"
        status.background = rounded((color and 0x00FFFFFF) or (0x33 shl 24), 20)
        status.setTextColor(color)
    }

    /** Harmless call to see whether this phone has the vivo / iQOO back light. */
    private fun probe() {
        io.execute {
            val r = LightCore.call("info", packageName, "")
            supported = r.startsWith("uid=") && !r.contains("hasLight=false") && !r.contains("case=-1")
            if (supported == false) say("This app works on vivo / iQOO phones that have a back light.")
            refreshStatus()
        }
    }

    // ---------- effect building ----------
    private fun hex(c: Int): String {
        val v = if (AuraConfig.SWAP_RED_BLUE) ((c and 0xFF) shl 16) or (c and 0xFF00) or ((c shr 16) and 0xFF) else c
        return String.format("%06X", v and 0xFFFFFF)
    }

    /** Milliseconds per cycle; 0 means steady. */
    private fun period() = if (speed <= 0) 0 else 4000 - (speed - 1) * 3500 / 99

    private fun buildJson(): String {
        val total = if (forever) 3_600_000 else durSec * 1000
        val e = JSONObject()
        e.put("lightName", AuraConfig.LIGHT_NAME)
        e.put("brightness", bright * 255 / 100)
        e.put("isCountPriority", false)
        e.put("totalTime", total)
        if (speed == 0) {
            // Steady: a breathing effect that fades in quickly, then just holds the light on.
            val rise = 150; val fall = 1; val close = 1
            e.put("type", "breathing")
            e.put("periodTime", total); e.put("count", 1)
            e.put("lightColor", hex(colors[0]))
            e.put("riseTime", rise); e.put("holdTime", total - rise - fall - close)
            e.put("fallTime", fall); e.put("closeTime", close)
        } else {
            val p = period()
            e.put("type", kinds[kind])
            e.put("periodTime", p); e.put("count", maxOf(1, (total + p - 1) / p))
            if (kind == 0) {
                val rise = p * 35 / 100; val hold = p * 15 / 100
                e.put("lightColor", hex(colors[0]))
                e.put("riseTime", rise); e.put("holdTime", hold); e.put("fallTime", rise)
                e.put("closeTime", p - rise * 2 - hold)
            } else {
                e.put("firstLightColor", hex(colors[0])); e.put("secondLightColor", hex(colors[1]))
                e.put("thirdLightColor", hex(colors[2])); e.put("fourthLightColor", hex(colors[3]))
                if (kind == 2) e.put("subType", 0)
            }
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
        preview.alpha = 1f
        if (speed > 0) {
            val a = ObjectAnimator.ofFloat(preview, View.ALPHA, 1f, 0.2f)
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
        preview.alpha = 1f
    }

    // ---------- presets ----------
    private fun stateJson() = JSONObject()
        .put("kind", kind).put("colors", JSONArray(colors.toList()))
        .put("dur", durSec).put("forever", forever).put("speed", speed).put("bright", bright)

    private fun applyState(o: JSONObject) {
        kind = o.optInt("kind", 0).coerceIn(0, 2)
        o.optJSONArray("colors")?.let { a -> for (i in 0 until minOf(4, a.length())) colors[i] = a.getInt(i) }
        durSec = o.optInt("dur", 10).coerceIn(1, 60)
        forever = o.optBoolean("forever", false)
        speed = o.optInt("speed", 50).coerceIn(0, 100)
        bright = o.optInt("bright", 100).coerceIn(5, 100)
        slot = 0
        if (this::plane.isInitialized) plane.setColor(colors[0])
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
            val cs = o.optJSONArray("colors")
            val first = if (cs != null && cs.length() > 0) cs.getInt(0) else Color.WHITE
            val rowV = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10)); background = rounded(cChip, 14)
                addView(View(context).apply { background = circle(first, Color.parseColor("#44FFFFFF")) },
                    LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(12) })
                addView(text(o.optString("name", "Preset"), 15f), LinearLayout.LayoutParams(0, wrap, 1f))
                addView(text("${kindLabels[o.optInt("kind", 0).coerceIn(0, 2)]} ▶", 13f, cSub))
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
