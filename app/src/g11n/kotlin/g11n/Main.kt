package g11n

import core.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

object Events {

}

class Main(
        urls: () -> Map<Url, Prefix>,
        doPutTranslation: (Key, Translation) -> Result<Boolean>
) {

    private val fetcher = TranslationsFetcher(urls, doPutTranslation = doPutTranslation)

    fun load(ktx: Kontext) = GlobalScope.async(COMMON) {
        fetcher.load(ktx)
    }

    fun sync(ktx: Kontext) = GlobalScope.async(COMMON) {
        fetcher.sync(ktx)
        fetcher.save(ktx)
    }

    fun invalidateCache(ktx: Kontext) = GlobalScope.async(COMMON) {
        fetcher.invalidateCache(ktx)
    }
}

val g11Manager by lazy {
    g11n.Main(
            urls = { mapOf(
                    pages.filtersStringsFallback().toExternalForm() to "filters",
                    pages.filtersStrings().toExternalForm() to "filters"
            ) },
            doPutTranslation = { key, value ->
                core.Result.of { i18n.set(key, value); true }
            }
    )
}
