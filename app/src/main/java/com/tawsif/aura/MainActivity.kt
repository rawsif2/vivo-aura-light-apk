package com.tawsif.aura

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val scanner = Executors.newSingleThreadExecutor()
    private var svc: ILightService? = null
    @Volatile private var scanning = false

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
    private var speed = 50
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
    private lateinit var adv: LinearLayout
    private lateinit var out: TextView
    private lateinit var modeSpin: Spinner
    private lateinit var pkg: EditText
    private lateinit var lightName: EditText
    private lateinit var swapRb: CheckBox
    private lateinit var subType: EditText
    private lateinit var jsonBox: EditText
    private var pulse: ObjectAnimator? = null
    private val doneRunnable = Runnable { stopPreview(); say("Done") }

    private val prefs by lazy { getSharedPreferences("aura", MODE_PRIVATE) }

    private val known = listOf(
        300 to "Incoming call", 400 to "Notification", 500 to "Photo flash",
        501 to "Secondary photo flash", 502 to "Mood show", 511 to "Charging plugin",
        512 to "Full charge", 513 to "Low-power charging", 514 to "Type 514",
        710 to "Media light", 800 to "Charging", 810 to "Brightness bar",
        811 to "Game brightness bar", 820 to "Boot on"
    )
    private val gameIds = (1601..1610).toList() + (1701..1703) + (1801..1809) + (1901..1907) + 2001
    private val shellPkg = "com.android.shell"
    private val modeNames = listOf("auto", "direct", "shizuku")
    @Volatile private var directOk: Boolean? = null
    private class Ctx(val mode: Int, val pkg: String)
    private fun ctx() = Ctx(modeSpin.selectedItemPosition, pkg.text.toString().trim())

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, LightUserService::class.java.name))
            .daemon(false).processNameSuffix("aura").version(1)
    }
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            svc = ILightService.Stub.asInterface(b); refresh()
        }
        override fun onServiceDisconnected(n: ComponentName?) { svc = null; refresh() }
    }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) bind() else say("Shizuku permission was denied.")
        refresh()
    }
    private val binderListener = Shizuku.OnBinderReceivedListener {
        autoConnect(); refresh()
    }
    private val deadListener = Shizuku.OnBinderDeadListener { svc = null; refresh() }

    // ---------- small UI helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun n(e: EditText, d: Int) = e.text.toString().trim().toIntOrNull() ?: d

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
    private fun field(hint: String, value: String = "", number: Boolean = false, lines: Int = 1) =
        EditText(this).apply {
            this.hint = hint; setText(value); typeface = Typeface.MONOSPACE; textSize = 14f
            setTextColor(Color.WHITE); setHintTextColor(cSub)
            if (lines > 1) { setSingleLine(false); minLines = lines; gravity = Gravity.TOP }
            if (number) inputType = InputType.TYPE_CLASS_NUMBER
        }
    private fun plainButton(label: String, onClick: () -> Unit) =
        Button(this).apply { text = label; isAllCaps = false; setOnClickListener { onClick() } }
    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it, LinearLayout.LayoutParams(0, wrap, 1f)) }
    }
    private fun advLabel(t: String) = text(t, 15f, Color.WHITE, true).apply { setPadding(0, dp(14), 0, dp(4)) }

    private fun log(s: String) = runOnUiThread { out.text = (s + "\n\n" + out.text).take(10000) }
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

        // header
        status = text("", 13f, Color.WHITE, true).apply {
            setPadding(dp(14), dp(7), dp(14), dp(7)); setOnClickListener { onStatusClick() }
        }
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
        hexField = field("RRGGBB").apply {
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
        plane.layoutParams = LinearLayout.LayoutParams(match, dp(170)).apply { topMargin = dp(12); bottomMargin = dp(12) }
        root.addView(card(title("Color"), slotRow, plane, swatchScroll))

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
            labelRow("Speed", speedLabel), speedSeek,
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

        // advanced
        buildAdvanced()
        val advToggle = pill("Advanced tools  ▾", cChip) {}
        advToggle.setOnClickListener {
            val open = adv.visibility != View.VISIBLE
            adv.visibility = if (open) View.VISIBLE else View.GONE
            advToggle.text = if (open) "Advanced tools  ▴" else "Advanced tools  ▾"
        }
        root.addView(advToggle, LinearLayout.LayoutParams(match, dp(48)).apply { topMargin = dp(16) })
        root.addView(adv)

        // restore last used look
        prefs.getString("last", null)?.let { try { applyState(JSONObject(it)) } catch (_: Throwable) {} }
        lightName.setText(prefs.getString("lightName", ""))
        swapRb.isChecked = prefs.getBoolean("swapRb", false)
        plane.setColor(colors[slot])
        syncUi()
        renderPresets()

        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permListener)
        refresh()
    }

    override fun onResume() { super.onResume(); autoConnect(); refresh(); if (directOk == null) probe() }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permListener)
        scanning = false
        pulse?.cancel()
        super.onDestroy()
    }

    // ---------- state -> UI ----------
    private fun syncUi(skipHex: Boolean = false) {
        kindButtons.forEachIndexed { i, b -> b.background = rounded(if (i == kind) cAccent else cChip, 14) }
        kindHint.text = kindHints[kind]
        val used = if (kind == 0) 1 else 4
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
        speedLabel.text = if (speed < 34) "Slow" else if (speed < 67) "Medium" else "Fast"
        brightLabel.text = "$bright%"
    }

    private fun refresh() = runOnUiThread {
        val sz = try {
            when {
                !Shizuku.pingBinder() -> 0
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> 1
                svc == null -> 2
                else -> 3
            }
        } catch (t: Throwable) { 0 }
        val (label, color) = when {
            directOk == true -> "Ready (no Shizuku needed)" to cGreen
            sz == 3 -> "Ready (via Shizuku)" to cGreen
            directOk == null -> "Checking…" to cOrange
            sz == 1 -> "Tap to allow Shizuku" to cOrange
            sz == 2 -> "Tap to connect Shizuku" to cOrange
            else -> "No access, tap for help" to cRed
        }
        status.text = "●  $label"
        status.background = rounded((color and 0x00FFFFFF) or (0x33 shl 24), 20)
        status.setTextColor(color)
    }

    private fun onStatusClick() {
        when {
            directOk == true -> say("Direct access works. Shizuku isn't needed.")
            directOk == null -> probe(true)
            else -> connect()
        }
    }

    /** Tries a harmless call as this app (no Shizuku) to see whether the service lets normal apps in. */
    private fun probe(manual: Boolean = false) {
        io.execute {
            val r = LightCore.call("info", packageName, "", 0, 0)
            val ok = r.startsWith("uid=") && !r.contains("hasLight=false") && !r.contains("case=-1")
            directOk = ok
            log("> direct access test (no Shizuku)\n$r")
            if (ok) { if (manual) say("Direct access works. Shizuku isn't needed.") }
            else say("This phone doesn't allow direct access. Start Shizuku and tap the status.")
            refresh()
        }
    }

    // ---------- effect building ----------
    private fun hex(c: Int): String {
        val v = if (swapRb.isChecked) ((c and 0xFF) shl 16) or (c and 0xFF00) or ((c shr 16) and 0xFF) else c
        return String.format("%06X", v and 0xFFFFFF)
    }

    private fun period() = 4000 - speed * 35

    private fun buildJson(): String {
        val t = kinds[kind]
        val p = period()
        val total = if (forever) 3_600_000 else durSec * 1000
        val e = JSONObject()
        e.put("type", t)
        e.put("lightName", lightName.text.toString().trim())
        e.put("brightness", bright * 255 / 100)
        e.put("periodTime", p)
        e.put("count", maxOf(1, (total + p - 1) / p))
        e.put("totalTime", total)
        e.put("isCountPriority", false)
        if (kind == 0) {
            val rise = p * 35 / 100; val hold = p * 15 / 100
            e.put("lightColor", hex(colors[0]))
            e.put("riseTime", rise); e.put("holdTime", hold); e.put("fallTime", rise)
            e.put("closeTime", p - rise * 2 - hold)
        } else {
            e.put("firstLightColor", hex(colors[0])); e.put("secondLightColor", hex(colors[1]))
            e.put("thirdLightColor", hex(colors[2])); e.put("fourthLightColor", hex(colors[3]))
            if (kind == 2) e.put("subType", n(subType, 0))
        }
        return JSONObject().put("combineName", "aura_custom").put("effects", JSONArray().put(e)).toString(2)
    }

    private fun startEffect() {
        val json = buildJson()
        jsonBox.setText(json)
        prefs.edit().putString("last", stateJson().toString())
            .putString("lightName", lightName.text.toString().trim())
            .putBoolean("swapRb", swapRb.isChecked).apply()
        val c = ctx()
        say("Starting…")
        startPreview()
        io.execute {
            exec(c, "stopall", 0, 0, "")
            val r = exec(c, "json", 0, 0, json)
            val id = r.substringAfter("-> ", "").trim().toIntOrNull()
            log("> start effect\n$r")
            if (id != null && id >= 0) {
                say("Playing ✓" + if (r.startsWith("[via Shizuku]")) " (via Shizuku)" else if (c.mode != 2) " (no Shizuku needed)" else "")
            } else {
                runOnUiThread { stopPreview() }
                say(when {
                    r.startsWith("Shell service") -> "Not connected. Tap the status at the top."
                    r.startsWith("ERROR") && svc == null && c.mode == 0 ->
                        "Direct access was refused. Start Shizuku and tap the status at the top."
                    lightName.text.isNullOrBlank() -> "Couldn't start. Open Advanced tools and set lightName."
                    else -> "Couldn't start. See Advanced tools > output."
                })
            }
        }
    }

    private fun stopEffect() {
        stopPreview()
        val c = ctx()
        io.execute { log("> stop\n" + exec(c, "stopall", 0, 0, "")); say("Stopped") }
    }

    private fun startPreview() {
        stopPreview()
        preview.alpha = 1f
        val a = ObjectAnimator.ofFloat(preview, View.ALPHA, 1f, 0.2f)
        a.duration = (period() / 2).toLong().coerceAtLeast(250L)
        a.repeatMode = ValueAnimator.REVERSE
        a.repeatCount = ValueAnimator.INFINITE
        a.start(); pulse = a
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

    // ---------- Shizuku ----------
    private fun autoConnect() {
        try {
            if (svc == null && Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bind()
        } catch (_: Throwable) {}
    }

    private fun connect() {
        try {
            when {
                !Shizuku.pingBinder() -> say("Open the Shizuku app and start it, then come back.")
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> bind()
                Shizuku.shouldShowRequestPermissionRationale() ->
                    say("Permission was denied. Allow this app in Shizuku > Authorized apps.")
                else -> Shizuku.requestPermission(100)
            }
        } catch (t: Throwable) { say("Connect failed: $t") }
    }

    private fun bind() {
        try { Shizuku.bindUserService(args, conn) } catch (t: Throwable) { log("Bind failed: $t") }
    }

    private fun failed(r: String): Boolean {
        if (r.startsWith("ERROR") || r.startsWith("Shell service")) return true
        val id = r.substringAfter("-> ", "").trim().toIntOrNull()
        return id != null && id < 0
    }

    /** mode 0 = auto (direct, then Shizuku), 1 = direct only, 2 = Shizuku only. */
    private fun exec(c: Ctx, op: String, a: Int, b: Int, arg: String): String {
        fun direct(): String = try {
            LightCore.call(op, c.pkg.ifEmpty { packageName }, arg, a, b)
        } catch (t: Throwable) { "ERROR: $t" }
        fun shell(): String = try {
            svc?.call(op, c.pkg.ifEmpty { shellPkg }, arg, a, b) ?: "Shell service not connected. Tap the status at the top."
        } catch (t: Throwable) { "ERROR: $t" }
        return when (c.mode) {
            1 -> direct()
            2 -> shell()
            else -> {
                val r = direct()
                if (failed(r) && svc != null) {
                    if (r.startsWith("ERROR")) { directOk = false; refresh() }
                    "[via Shizuku] " + shell()
                } else r
            }
        }
    }

    private fun go(op: String, a: Int = 0, b: Int = 0, arg: String = "") {
        val c = ctx()
        io.execute { log("> $op (mode=${modeNames[c.mode]})\n" + exec(c, op, a, b, arg)) }
    }

    private fun scan(ids: List<Int>) {
        if (scanning) return
        val c = ctx()
        if (c.mode == 2 && svc == null) { log("Connect Shizuku first."); return }
        scanning = true
        scanner.execute {
            log("Scan of ${ids.size} IDs started. Accepted IDs are listed; watch the back light.")
            var hits = 0
            for (id in ids) {
                if (!scanning) break
                val r = exec(c, "duration", id, 1500, "").substringAfter("-> ", "").trim().toIntOrNull()
                if (r != null && r >= 0) {
                    hits++; log("type $id accepted (ret=$r)")
                    Thread.sleep(1700); exec(c, "stoptype", id, 0, "")
                }
            }
            scanning = false
            log("Scan finished, $hits accepted.")
        }
    }

    // ---------- advanced ----------
    private fun buildAdvanced() {
        adv = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        out = text("", 12f, Color.WHITE).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }
        modeSpin = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Auto: try direct, then Shizuku", "Direct only (no Shizuku)", "Shizuku only"))
            setSelection(prefs.getInt("mode", 0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    prefs.edit().putInt("mode", pos).apply()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        pkg = field("Caller package override (blank = automatic)", "")
        lightName = field("lightName (value that works on your phone)")
        swapRb = CheckBox(this).apply { text = "Swap red/blue (if colors look wrong)"; setTextColor(Color.WHITE) }
        subType = field("Chase subType", "0", true)
        jsonBox = field("Last JSON sent", "", lines = 6)
        val type = field("Light type", "400", true)
        val dur = field("Duration ms", "5000", true)
        val times = field("Repeat times", "3", true)
        val from = field("From", "1", true)
        val to = field("To", "999", true)
        val cmd = field("Shell command", "dumpsys vivo_light_service")
        val presets = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                known.map { "${it.first}  ${it.second}" })
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    type.setText(known[pos].first.toString())
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

        adv.addView(card(
            advLabel("Connection and effect settings"),
            modeSpin, pkg, lightName, swapRb, subType,
            row(plainButton("Check light") { go("info") }, plainButton("Stop all") { go("stopall") }),
            plainButton("Test direct access (no Shizuku)") { probe(true) }
        ))
        adv.addView(card(
            advLabel("Effect by type ID"), presets, row(type, dur, times),
            row(plainButton("Start (ms)") { go("duration", n(type, 400), n(dur, 5000)) },
                plainButton("Start (times)") { go("times", n(type, 400), n(times, 3)) }),
            row(plainButton("Start (no limit)") { go("start", n(type, 400)) },
                plainButton("Stop this type") { go("stoptype", n(type, 400)) })
        ))
        adv.addView(card(
            advLabel("Find valid type IDs (1.5s each)"), row(from, to),
            row(plainButton("Scan range") { scan((n(from, 1)..n(to, 999)).toList()) },
                plainButton("Scan known IDs") { scan(known.map { it.first }) }),
            plainButton("Scan game IDs (inferred)") { scan(gameIds) },
            plainButton("Stop scan") { scanning = false }
        ))
        adv.addView(card(
            advLabel("Raw JSON"), jsonBox,
            plainButton("Start raw JSON") { go("json", arg = jsonBox.text.toString()) }
        ))
        adv.addView(card(
            advLabel("Debug"), cmd,
            row(plainButton("Run shell") { go("shell", arg = cmd.text.toString()) },
                plainButton("Service logs") { go("logs") }),
            plainButton("Clear output") { out.text = "" }, out
        ))
    }
}
