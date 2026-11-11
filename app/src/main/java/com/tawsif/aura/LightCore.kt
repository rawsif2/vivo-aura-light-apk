package com.tawsif.aura

import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Talks to vivo's framework class. Runs in the app (direct mode) or inside the Shizuku shell process. */
object LightCore {
    private val I: Class<*> = Int::class.javaPrimitiveType!!
    private val S: Class<*> = String::class.java

    private val cls: Class<*> by lazy {
        try { HiddenApiBypass.addHiddenApiExemptions("L") } catch (_: Throwable) {}
        Class.forName("com.vivo.framework.vivolight.VivoLightManager")
    }
    private val mgr: Any by lazy { cls.getMethod("getInstance").invoke(null)!! }

    private fun inv(name: String, types: List<Class<*>>, vararg args: Any?): Any? =
        cls.getMethod(name, *types.toTypedArray()).invoke(mgr, *args)

    fun call(op: String, pkg: String, arg: String, a: Int, b: Int): String = try {
        when (op) {
            "info" -> "uid=${android.os.Process.myUid()} hasLight=${inv("hasLight", emptyList())} " +
                "case=${inv("getLightCase", emptyList())} type=${inv("getLightType", emptyList())}"
            "start" -> "startLight -> ${inv("startLight", listOf(I, S), a, pkg)}"
            "duration" -> "startLightByDuration -> ${inv("startLightByDuration", listOf(I, S, I), a, pkg, b)}"
            "times" -> "startLightByTimes -> ${inv("startLightByTimes", listOf(I, S, I), a, pkg, b)}"
            "json" -> "startLight(json) -> ${inv("startLight", listOf(S, S), pkg, arg)}"
            "stoptype" -> { inv("stopLightByLightType", listOf(I, S), a, pkg); "stopLightByLightType done" }
            "stopall" -> { inv("stopLightAll", emptyList()); "stopLightAll done" }
            "logs" -> sh("logcat -d -t 80 -s DynamicLight-VivoLightManager:V DynamicLight-VivoLightManagerService:V")
            "shell" -> sh(arg)
            else -> "unknown op: $op"
        }
    } catch (e: Throwable) {
        "ERROR: " + (e.cause ?: e)
    }

    private fun sh(cmd: String): String {
        val p = ProcessBuilder("timeout", "20", "sh", "-c", cmd).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return text.takeLast(6000).ifBlank { "(no output)" }
    }
}
