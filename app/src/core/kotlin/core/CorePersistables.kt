package core

/**
 * Those files cannot change their (package) name because they are persisted.
 */

data class LoggerConfig(
        val active: Boolean = false,
        val logAllowed: Boolean = false,
        val logDenied: Boolean = false
): Persistable {
    override fun key() = "logger:config"
}
