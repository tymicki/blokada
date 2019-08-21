package core

import android.content.Context
import com.github.salomonbrys.kodein.Kodein
import java.util.*

class AndroidKontext(
        id: Any,
        val ctx: Context,
        val di: () -> Kodein = {
            val c = ctx.applicationContext
            if (c is MainApplication) c.kodein
            else throw Exception("app does not use kodein")
        }
): Kontext(id)

private val kontexts = WeakHashMap<Any, AndroidKontext>()

fun Context.ktx(id: String = "ctx") = kontexts.getOrPut(id, {
    AndroidKontext(id, this) } ) as AndroidKontext

