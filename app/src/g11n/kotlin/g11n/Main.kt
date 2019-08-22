package g11n

import core.COMMON
import core.Kontext
import core.Result
import core.Url
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
