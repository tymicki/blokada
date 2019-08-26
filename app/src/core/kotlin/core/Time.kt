package core

val time = SystemTime()

@Deprecated("out")
class SystemTime {
    fun now(): Long {
        return System.currentTimeMillis()
    }
}

