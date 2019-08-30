package g11n

import core.*
import java.net.URL
import java.util.*
import kotlin.Result.Companion.failure

internal class TranslationsFetcher(
        val urls: () -> Map<Url, Prefix>,
        val doValidateCacheForUrl: (TranslationStore, Url) -> Boolean = { store, url ->
            store.get(url) + 86400 * 1000 > System.currentTimeMillis()
        },
        val doFetchTranslations: (Url, Prefix) -> Result<Translations> = { url, prefix ->
            runCatching {
                val prop = Properties()
                prop.load(createStream(openUrl(URL(url), 10 * 1000)()))
                prop.stringPropertyNames().map { key -> "${prefix}_$key" to prop.getProperty(key)}
            }
        },
        val doPutTranslation: (Key, Translation) -> Result<Boolean> = { key, translation ->
            failure(Exception("nowhere to put translations"))
        }
) {

    private var store = TranslationStore()

    @Synchronized fun load() {
        store = get(TranslationStore::class.java)
    }

    @Synchronized fun save() {
        store.update(TranslationStore::class.java)
    }

    @Synchronized fun sync() {
        val invalid = urls().filter { !doValidateCacheForUrl(store, it.key) }
        v("attempting to fetch ${invalid.size} translation urls")

        var failed = 0
        invalid.map { (url, prefix) -> doFetchTranslations(url, prefix).fold(
                onSuccess = {
                    v("translation fetched", url, it.size)
                    url to it
                },
                onFailure = { ex ->
                    e("failed fetching translation", url, ex)
                    ++failed
                    url to emptyTranslations()
                }
        ) }.filter { it.second.isNotEmpty() }.forEach { (url, translations) ->
            store = store.put(url)
            translations.forEach { (key, value) ->
                doPutTranslation(key, value).onFailure { ex -> e("failed putting translation", ex) }
            }
        }

        v("finished fetching translations; $failed failed")
    }

    @Synchronized fun invalidateCache() {
        v("invalidating translations cache")
        store = TranslationStore()
    }
}
