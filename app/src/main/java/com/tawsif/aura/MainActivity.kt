package com.tawsif.aura

import android.app.Activity
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class MainActivity : Activity() {
    companion object {
        private const val SERVICE_NAME = "vivo_light_service"
        private const val DESCRIPTOR = "vivo.app.vivolight.IVivoLightManager"
        private const val REQUEST_CODE = 4107
    }

    private lateinit var status: TextView
    private var serviceBinder: IBinder? = null
    private var shizukuGranted = false

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                shizukuGranted = grantResult == PackageManager.PERMISSION_GRANTED
                setStatus(if (shizukuGranted) {
                    "Shizuku permission granted. Tap Check Aura Light."
                } else {
                    "Shizuku permission denied."
                })
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(content)

        val title = TextView(this).apply {
            text = "AURA LIGHT"
            textSize = 27f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Vivo / iQOO framework controller"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        status = TextView(this).apply {
            text = "Start Shizuku, then connect."
            textSize = 15f
            setPadding(16, 18, 16, 18)
            setBackgroundColor(0xFF20252B.toInt())
        }

        content.addView(title, matchWrap())
        content.addView(subtitle, matchWrap())
        content.addView(status, matchWrap())

        addAction(content, "Connect Shizuku", ::connectShizuku)
        addAction(content, "Check Aura Light", ::checkAuraLight)
        addAction(content, "Stop All Lights", ::stopAllLights)

        val note = TextView(this).apply {
            text = "\nEffect starting is not enabled in this build. The Vivo startLight() call needs the exact VivoLightRecord Parcelable and supported segment data for this firmware. Sending guessed parcel data could fail or trigger the wrong hardware behavior."
            textSize = 13f
            setPadding(4, 24, 4, 8)
        }
        content.addView(note, matchWrap())
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)

        Shizuku.addRequestPermissionResultListener(permissionListener)
        shizukuGranted = Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = 14 }

    private fun addAction(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }
        parent.addView(button, matchWrap())
    }

    private fun connectShizuku() {
        if (!Shizuku.pingBinder()) {
            setStatus("Shizuku is not running. Start Shizuku first.")
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            shizukuGranted = true
            setStatus("Shizuku is ready. Tap Check Aura Light.")
            return
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            setStatus("Please grant Shizuku permission to this app.")
        }
        Shizuku.requestPermission(REQUEST_CODE)
    }

    private fun getService(): IBinder {
        if (!Shizuku.pingBinder()) throw IllegalStateException("Shizuku is not running")
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Shizuku permission has not been granted")
        }
        val raw = SystemServiceHelper.getSystemService(SERVICE_NAME)
            ?: throw IllegalStateException("Vivo service '$SERVICE_NAME' was not found")
        val wrapped = ShizukuBinderWrapper(raw)
        if (!wrapped.isBinderAlive) throw IllegalStateException("Vivo service Binder is not alive")
        serviceBinder = wrapped
        return wrapped
    }

    private fun transact(code: Int, writer: (Parcel) -> Unit = {}, reader: (Parcel) -> String): String {
        val binder = getService()
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            writer(data)
            val ok = binder.transact(code, data, reply, 0)
            if (!ok) throw IllegalStateException("Binder transaction $code was rejected")
            reply.readException()
            return reader(reply)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun checkAuraLight() {
        runCatching {
            val has = transact(1, reader = { (it.readInt() != 0).toString() })
            val lightCase = transact(2, reader = { it.readInt().toString() })
            val lightType = transact(3, reader = { it.readInt().toString() })
            setStatus(
                "Vivo service connected.\n" +
                "hasLight: $has\n" +
                "lightCase: $lightCase\n" +
                "lightType: $lightType"
            )
        }.onFailure { setStatus("Check failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun stopAllLights() {
        runCatching {
            transact(8, reader = { "Stop-all command sent." })
            setStatus("Stop-all command sent to Vivo service.")
        }.onFailure { setStatus("Stop failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun setStatus(message: String) {
        runOnUiThread { status.text = message }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}
