package g11n

import android.annotation.TargetApi
import android.content.res.Resources
import core.Format
import core.Resource
import core.getApplicationContext
import core.v
import core.Persistence
import core.PersistenceWithSerialiser
import core.newPersistedProperty2
import core.repo
import kotlinx.coroutines.runBlocking
import org.blokada.R
import java.util.*

typealias LanguageTag = String
typealias Localised = String

val i18n by lazy {
    runBlocking {
        I18nImpl()
    }
}

private val persistences = mutableMapOf<String, I18nPersistence>()
@Synchronized fun persistenceFor(name: String) : I18nPersistence {
    if (!persistences.containsKey(name)) persistences[name] = I18nPersistence(name)
    return persistences[name]!!
}

class I18nImpl {

    private val ctx by lazy {
        runBlocking { getApplicationContext()!! }
    }

    private val res: Resources by lazy { ctx.resources }

    fun contentUrl(): String {
        return "%s/%s".format(repo.content().contentPath ?: "http://localhost", locale())
    }

    fun fallbackContentUrl(): String {
        return "%s/%s".format(repo.content().contentPath ?: "http://localhost", "en")
    }

    val locale = newPersistedProperty2("locale", { "en" },
            refresh = {
                val preferred = getPreferredLocales()
                val available = repo.content().locales
                v("locale preferred/available", preferred, available)

                /**
                 * Try matching exactly, if not, try matching by language tag. Use order of preferred
                 * locales defined by user.
                 */
                val availableLanguageTags = available.map { Locale(it.language) to it }.toMap()
                val matches = preferred.asSequence().map {
                    val justLanguageTag = Locale(it.language)
                    val tagAndCountry = Locale(it.language, it.country)
                    when {
                        available.contains(it) -> it
                        available.contains(tagAndCountry) -> tagAndCountry
                        available.contains(justLanguageTag) -> justLanguageTag
                        availableLanguageTags.containsKey(justLanguageTag) -> availableLanguageTags[justLanguageTag]
                        else -> null
                    }
                }.filterNotNull()
                v("locale matches", matches)
                (matches.firstOrNull() ?: Locale("en")).toString()
            })

    private val localisedMap: MutableMap<LanguageTag, MutableMap<Key, Localised>> = mutableMapOf()

    val localised = { key: Any ->
        localisedOrNull(key) ?: key.toString()
    }

    val localisedOrNull = { key: Any ->
        // Map resId to actual string key defined in xml files since we use them dynamically
        val (isResource, realKey) = if (key is Int) true to res.getResourceName(key) else false to key.toString()

        // Get all cached translations for current locale
        val strings = localisedMap.getOrPut(locale(), { mutableMapOf<Key, Localised>() })

        // If cache miss, try getting it from resources
        var string = strings.get(realKey)
        if (string == null || isResource) {
            val id = res.getIdentifier(realKey, "string", ctx.packageName)
            if (id != 0) {
                string = res.getString(id)
                strings.put(realKey, string)
            }
        }
        string
    }

    private val persistence: (LanguageTag) -> Persistence<Map<Key, LanguageTag>> = { tag ->
        persistenceFor(tag)
    }

    val set: (key: Any, value: String) -> Unit
        get() = { key, value ->
            val strings = localisedMap.getOrPut(locale(), { mutableMapOf<Key, Localised>() })
            strings.put(key.toString(), value)
            persistence(locale()).write(strings)
        }

    fun getString(resId: Int): String {
        return localised(resId)
    }

    fun getString(resId: Int, vararg arguments: Any): String {
        return localised(resId).format(*arguments)
    }

    fun getQuantityString(resId: Int, quantity: Int, vararg arguments: Any): String {
        // Intentionally no support for quantity strings for now
        return localised(resId).format(*arguments)
    }

    fun getString(resource: Resource): String {
        return when {
            resource.hasResId() -> localised(resource.getResId())
            else -> resource.getString()
        }
    }

    init {
        repo.content.doWhenSet().then {
            v("refreshing locale")
            locale.refresh(force = true)
        }
        locale.doWhenSet().then {
            val strings = localisedMap.getOrPut(locale(), { mutableMapOf<Key, Localised>() })
            strings.putAll(persistence(locale()).read(strings))
            Format.setup(ctx, locale())
        }
    }

}

class I18nPersistence(
        private val locale: LanguageTag
) : PersistenceWithSerialiser<Map<Key, Localised>>() {

    val p by lazy { serialiser("i18n_$locale") }

    override fun read(current: Map<Key, Localised>): Map<Key, Localised> {
        val count = p.getInt("keys", 0)
        val map = IntRange(0, count - 1).map {
            p.getString("k_$it", "") to p.getString("v_$it", "")
        }.filter { it.first.isNotBlank() && it.second.isNotEmpty() }.toMap()
        return if (map.isNotEmpty()) map else current
    }

    override fun write(source: Map<Key, Localised>) {
        val e = p.edit()
        e.putInt("keys", source.size)
        var i = 0
        source.forEach { (k, v) ->
            e.putString("k_$i", k)
            e.putString("v_$i", v)
            i++
        }
        e.apply()
    }

}

fun I18nImpl.getBrandedString(resId: Int): String {
    return getString(resId, getString(R.string.branding_app_name_short))
}

@TargetApi(24)
internal fun getPreferredLocales(): List<java.util.Locale> {
    val cfg = android.content.res.Resources.getSystem().configuration
    return try {
        // Android, a custom list type that is not an iterable. Just wow.
        val locales = cfg.locales
        (0..locales.size() - 1).map { locales.get(it) }
    } catch (t: Throwable) { listOf(cfg.locale) }
}

