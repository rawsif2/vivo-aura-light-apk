package com.tawsif.aura

import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Talks to vivo's framework light manager directly from this app. */
object LightCore {
    private val S: Class<*> = String::class.java

    private val cls: Class<*> by lazy {
        try { HiddenApiBypass.addHiddenApiExemptions("L") } catch (_: Throwable) {}
        Class.forName("com.vivo.framework.vivolight.VivoLightManager")
    }
    private val mgr: Any by lazy { cls.getMethod("getInstance").invoke(null)!! }

    private fun inv(name: String, types: List<Class<*>>, vararg args: Any?): Any? =
        cls.getMethod(name, *types.toTypedArray()).invoke(mgr, *args)

    fun call(op: String, pkg: String, arg: String): String = try {
        when (op) {
            "info" -> "uid=${android.os.Process.myUid()} hasLight=${inv("hasLight", emptyList())} " +
                "case=${inv("getLightCase", emptyList())}"
            "json" -> "startLight(json) -> ${inv("startLight", listOf(S, S), pkg, arg)}"
            "stopall" -> { inv("stopLightAll", emptyList()); "stopLightAll done" }
            else -> "unknown op: $op"
        }
    } catch (e: Throwable) {
        "ERROR: " + (e.cause ?: e)
    }
}
