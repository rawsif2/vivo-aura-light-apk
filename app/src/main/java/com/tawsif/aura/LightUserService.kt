package com.tawsif.aura

/** Started by Shizuku as the shell user (uid 2000). */
class LightUserService : ILightService.Stub() {
    override fun destroy() { System.exit(0) }
    override fun call(op: String?, pkg: String?, arg: String?, a: Int, b: Int): String =
        LightCore.call(op ?: "", pkg ?: "", arg ?: "", a, b)
}
