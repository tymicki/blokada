package g11n

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.mapError
import core.*
import kotlinx.coroutines.runBlocking
import java.net.URL
import java.util.*

internal class TranslationsFetcher(
        val urls: () -> Map<Url, Prefix>,
        val doValidateCacheForUrl: (TranslationStore, Url) -> Boolean = { store, url ->
            store.get(url) + 86400 * 1000 > System.currentTimeMillis()
        },
        val doFetchTranslations: (Url, Prefix) -> Result<Translations> = { url, prefix ->
            Result.of {
                val prop = Properties()
                prop.load(createStream(openUrl(URL(url), 10 * 1000)()))
                prop.stringPropertyNames().map { key -> "${prefix}_$key" to prop.getProperty(key)}
            }
        },
        val doPutTranslation: (Key, Translation) -> Result<Boolean> = { key, translation ->
            Err(Exception("nowhere to put translations"))
        }
) {

    private var store = TranslationStore()

    @Synchronized fun load(ktx: Kontext) {
        store = runBlocking { store.loadFromPersistence() }
    }

    @Synchronized fun save(ktx: Kontext) {
        runBlocking { store.saveToPersistence() }
    }

    @Synchronized fun sync(ktx: Kontext) {
        val invalid = urls().filter { !doValidateCacheForUrl(store, it.key) }
        v("attempting to fetch ${invalid.size} translation urls")

        var failed = 0
        invalid.map { (url, prefix) -> doFetchTranslations(url, prefix).mapBoth(
                success = {
                    v("translation fetched", url, it.size)
                    url to it
                },
                failure = { ex ->
                    e("failed fetching translation", url, ex)
                    ++failed
                    url to emptyTranslations()
                }
        ) }.filter { it.second.isNotEmpty() }.forEach { (url, translations) ->
            store = store.put(url)
            translations.forEach { (key, value) ->
                doPutTranslation(key, value).mapError { ex -> e("failed putting translation", ex) }
            }
        }

        v("finished fetching translations; $failed failed")
    }

    @Synchronized fun invalidateCache(ktx: Kontext) {
        v("invalidating translations cache")
        store = TranslationStore()
    }
}
