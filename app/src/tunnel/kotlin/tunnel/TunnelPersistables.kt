package tunnel

import core.Persistable

/**
 * Those files cannot change their (package) name because they are persisted.
 */

data class TunnelPause(
        val vpn: Boolean = false,
        val adblocking: Boolean = false,
        val dns: Boolean = false
): Persistable {
    override fun key() = "tunnel:pause"
}

// TODO: rename to something else
data class TunnelConfig(
        val wifiOnly: Boolean = true,
        val firstLoad: Boolean = true,
        val powersave: Boolean = false,
        val dnsFallback: Boolean = true,
        val report: Boolean = false,
        val cacheTTL: Long = 86400
): Persistable {
    override fun key() = "tunnel:config"
}
