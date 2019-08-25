package core

import android.content.Context
import java.util.*

class AndroidKontext(
        id: Any,
        val ctx: Context
): Kontext(id)

private val kontexts = WeakHashMap<Any, AndroidKontext>()

fun Context.ktx(id: String = "ctx") = kontexts.getOrPut(id, {
    AndroidKontext(id, this) } ) as AndroidKontext

