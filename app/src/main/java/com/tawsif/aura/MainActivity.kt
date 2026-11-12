package com.tawsif.aura

import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.view.Gravity
import android.view.View
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

    private lateinit var out: TextView
    private lateinit var status: TextView
    private lateinit var shellMode: CheckBox
    private lateinit var pkg: EditText

    private val known = listOf(
        300 to "Incoming call", 400 to "Notification", 500 to "Photo flash",
        501 to "Secondary photo flash", 502 to "Mood show", 511 to "Charging plugin",
        512 to "Full charge", 513 to "Low-power charging", 710 to "Media light",
        800 to "Charging", 810 to "Brightness bar", 811 to "Game brightness bar", 820 to "Boot on"
    )
    private val shellPkg = "com.android.shell"
    private val gameIds = (1601..1610).toList() + (1701..1703) + (1801..1809) + (1901..1907) + 2001

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, LightUserService::class.java.name))
            .daemon(false).processNameSuffix("aura").version(1)
    }
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            svc = ILightService.Stub.asInterface(b); log("Shell service connected."); refresh()
        }
        override fun onServiceDisconnected(n: ComponentName?) { svc = null; refresh() }
    }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) bind() else log("Shizuku permission denied.")
        refresh()
    }
    private val binderListener = Shizuku.OnBinderReceivedListener {
        try { if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bind() } catch (_: Throwable) {}
        refresh()
    }
    private val deadListener = Shizuku.OnBinderDeadListener { svc = null; refresh() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun n(e: EditText, d: Int) = e.text.toString().trim().toIntOrNull() ?: d

    private fun field(hint: String, value: String = "", number: Boolean = false, lines: Int = 1) =
        EditText(this).apply {
            this.hint = hint; setText(value); typeface = Typeface.MONOSPACE; textSize = 14f
            if (lines > 1) { setSingleLine(false); minLines = lines; gravity = Gravity.TOP }
            if (number) inputType = InputType.TYPE_CLASS_NUMBER
        }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply { text = label; isAllCaps = false; setOnClickListener { onClick() } }

    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 16f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(16), 0, dp(4))
    }

    private fun log(s: String) = runOnUiThread { out.text = (s + "\n\n" + out.text).take(10000) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        status = TextView(this)
        out = TextView(this).apply { typeface = Typeface.MONOSPACE; textSize = 12f; setTextIsSelectable(true) }
        shellMode = CheckBox(this).apply {
            text = "Run as shell via Shizuku (try this if direct mode is rejected)"
            isChecked = true
            setOnCheckedChangeListener { _, c -> pkg.setText(if (c) shellPkg else packageName) }
        }
        pkg = field("Caller package", shellPkg)
        val type = field("Light type", "400", true)
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
        val dur = field("Duration ms", "5000", true)
        val times = field("Repeat times", "3", true)
        val kinds = listOf("breathing", "strobe", "marquee")
        val kind = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, kinds)
        }
        val lightName = field("lightName (see debug)", "")
        val bright = field("brightness", "255", true)
        val period = field("periodTime", "2000", true)
        val count = field("count", "3", true)
        val total = field("totalTime", "6000", true)
        val prio = CheckBox(this).apply { text = "isCountPriority" }
        val c1 = field("color1 hex", "FF0000"); val c2 = field("color2 hex", "00FF00")
        val c3 = field("color3 hex", "0000FF"); val c4 = field("color4 hex", "FFFFFF")
        val rise = field("riseTime", "500", true); val hold = field("holdTime", "500", true)
        val fall = field("fallTime", "500", true); val close = field("closeTime", "500", true)
        val sub = field("marquee subType", "0", true)
        val json = field("JSON effect", "", lines = 6)

        fun hex(e: EditText): String {
            val t = e.text.toString().trim().removePrefix("#").take(6)
            return if (t.isNotEmpty() && t.toIntOrNull(16) != null) t else "FF0000"
        }
        fun build(): String {
            val t = kinds[kind.selectedItemPosition]
            val e = JSONObject()
            e.put("type", t); e.put("lightName", lightName.text.toString().trim())
            e.put("brightness", n(bright, 255)); e.put("periodTime", n(period, 2000))
            e.put("count", n(count, 3)); e.put("totalTime", n(total, 6000))
            e.put("isCountPriority", prio.isChecked)
            if (t == "breathing") {
                e.put("lightColor", hex(c1))
                e.put("riseTime", n(rise, 500)); e.put("holdTime", n(hold, 500))
                e.put("fallTime", n(fall, 500)); e.put("closeTime", n(close, 500))
            } else {
                e.put("firstLightColor", hex(c1)); e.put("secondLightColor", hex(c2))
                e.put("thirdLightColor", hex(c3)); e.put("fourthLightColor", hex(c4))
                if (t == "marquee") e.put("subType", n(sub, 0))
            }
            return JSONObject().put("combineName", "aura_custom")
                .put("effects", JSONArray().put(e)).toString(2)
        }
        val from = field("From", "1", true)
        val to = field("To", "100", true)
        val cmd = field("Shell command", "dumpsys vivo_light_service")

        root.addView(TextView(this).apply { text = "Vivo / iQOO Aura Light"; textSize = 22f })
        root.addView(status)
        root.addView(button("Connect Shizuku") { connect() })
        root.addView(shellMode)
        root.addView(pkg)
        root.addView(row(button("Check light") { go("info") }, button("Stop all") { go("stopall") }))

        root.addView(label("Effect by type ID"))
        root.addView(presets)
        root.addView(row(type, dur, times))
        root.addView(row(
            button("Start (ms)") { go("duration", n(type, 400), n(dur, 5000)) },
            button("Start (times)") { go("times", n(type, 400), n(times, 3)) }))
        root.addView(row(
            button("Start (no limit)") { go("start", n(type, 400)) },
            button("Stop this type") { go("stoptype", n(type, 400)) }))

        root.addView(label("Custom effect (breathing uses color1 only)"))
        root.addView(row(lightName, kind))
        root.addView(row(bright, period, count, total))
        root.addView(prio)
        root.addView(row(c1, c2)); root.addView(row(c3, c4))
        root.addView(row(rise, hold, fall, close))
        root.addView(sub)
        root.addView(row(
            button("Build JSON") { json.setText(build()) },
            button("Start effect") { val j = build(); json.setText(j); go("json", arg = j) }))
        root.addView(json)
        root.addView(button("Start raw JSON") { go("json", arg = json.text.toString()) })

        root.addView(label("Find valid type IDs (1.5s each, watch the light)"))
        root.addView(row(from, to))
        root.addView(row(
            button("Scan range") { scan((n(from, 1)..n(to, 100)).toList()) },
            button("Scan known IDs") { scan(known.map { it.first }) }))
        root.addView(button("Scan game IDs (1601-2001, inferred)") { scan(gameIds) })
        root.addView(button("Stop scan") { scanning = false })

        root.addView(label("Debug"))
        root.addView(cmd)
        root.addView(row(
            button("Run shell") { go("shell", arg = cmd.text.toString()) },
            button("Service logs") { go("logs") }))
        root.addView(button("Clear output") { out.text = "" })
        root.addView(out)
        setContentView(ScrollView(this).apply { addView(root) })

        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permListener)
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permListener)
        scanning = false
        super.onDestroy()
    }

    private fun refresh() = runOnUiThread {
        status.text = try {
            when {
                !Shizuku.pingBinder() -> "Shizuku: not running (direct mode only)"
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> "Shizuku: running, permission needed"
                svc == null -> "Shizuku: authorized, shell service not bound"
                else -> "Shizuku: shell service connected"
            }
        } catch (t: Throwable) { "Shizuku: $t" }
    }

    private fun connect() {
        try {
            when {
                !Shizuku.pingBinder() -> log("Shizuku isn't running. Open the Shizuku app, start it, then retry.")
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> bind()
                Shizuku.shouldShowRequestPermissionRationale() ->
                    log("Permission was denied permanently. Grant it in Shizuku > Authorized apps.")
                else -> Shizuku.requestPermission(100)
            }
        } catch (t: Throwable) { log("Connect failed: $t") }
    }

    private fun bind() {
        try { Shizuku.bindUserService(args, conn) } catch (t: Throwable) { log("Bind failed: $t") }
    }

    private fun exec(op: String, p: String, sh: Boolean, a: Int, b: Int, arg: String): String = try {
        if (sh) svc?.call(op, p, arg, a, b) ?: "Shell service not connected. Tap Connect Shizuku."
        else LightCore.call(op, p, arg, a, b)
    } catch (t: Throwable) { "ERROR: $t" }

    private fun go(op: String, a: Int = 0, b: Int = 0, arg: String = "") {
        val p = pkg.text.toString().trim(); val sh = shellMode.isChecked
        io.execute { log("> $op (pkg=$p, ${if (sh) "shell" else "direct"})\n" + exec(op, p, sh, a, b, arg)) }
    }

    private fun scan(ids: List<Int>) {
        if (scanning) return
        val p = pkg.text.toString().trim(); val sh = shellMode.isChecked
        if (sh && svc == null) { log("Connect Shizuku first."); return }
        scanning = true
        scanner.execute {
            log("Scan of ${ids.size} IDs started. Accepted IDs are listed; watch the back light.")
            var hits = 0
            for (id in ids) {
                if (!scanning) break
                val r = exec("duration", p, sh, id, 1500, "").substringAfter("-> ", "").trim().toIntOrNull()
                if (r != null && r >= 0) {
                    hits++; log("type $id accepted (ret=$r)")
                    Thread.sleep(1700); exec("stoptype", p, sh, id, 0, "")
                }
            }
            scanning = false
            log("Scan finished, $hits accepted.")
        }
    }
}
