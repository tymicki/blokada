package gs.environment

import android.util.Log

interface Journal {
    fun event(vararg events: Any)
}

class ALogcatJournal(private val tag: String) : Journal {

    override fun event(vararg events: Any) {
        Log.i(tag, "event: ${events.joinToString(separator = ";")}")
    }

}
