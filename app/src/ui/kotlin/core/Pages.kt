package core

import g11n.i18n
import java.net.HttpURLConnection
import java.net.URL

val pages by lazy {
    PagesImpl()
}

class PagesImpl {

    init {
        i18n.locale.doWhenSet().then {
            val c = i18n.contentUrl()
            if (!c.startsWith("http://localhost")) {
                v("setting content url", c)
                intro %= URL("$c/intro_vpn.html")
                updated %= URL("$c/updated.html")
                cleanup %= URL("$c/cleanup.html")
                patronAbout %= URL("$c/patron.html")
                cta %= URL("$c/cta.html")
                donate %= URL("$c/donate.html")
                help %= URL("$c/help.html")
                changelog %= URL("$c/changelog.html")
                credits %= URL("$c/credits.html")
                filters %= URL("$c/filters.txt")
                filtersStrings %= URL("$c/filters.properties")
                filtersStringsFallback %= URL("${i18n.fallbackContentUrl()}/filters.properties")
                dns %= URL("$c/dns.txt")
                dnsStrings %= URL("$c/dns.properties")
                patron %= resolveRedirect(patron())
                chat %= if (i18n.locale().startsWith("es")) {
                    URL("http://go.blokada.org/es_chat")
                } else URL("http://go.blokada.org/chat")
                vpn %= URL("$c/vpn.html")

                loaded %= true
            }
        }
    }

    val loaded = newProperty({ false })
    val intro = newProperty({ URL("http://localhost") })
    val updated = newProperty({ URL("http://localhost") })
    val patronAbout = newProperty({ URL("http://localhost") })
    val cleanup = newProperty({ URL("http://localhost") })
    val cta = newProperty({ URL("http://localhost") })
    val donate = newProperty({ URL("http://localhost") })
    val help = newProperty({ URL("http://localhost") })
    val changelog = newProperty({ URL("http://localhost") })
    val credits = newProperty({ URL("http://localhost") })
    val filters = newProperty({ URL("http://localhost") })
    val filtersStrings = newProperty({ URL("http://localhost") })
    val filtersStringsFallback = newProperty({ URL("http://localhost") })
    val dns = newProperty({ URL("http://localhost") })
    val dnsStrings = newProperty({ URL("http://localhost") })
    val chat = newProperty({ URL("http://go.blokada.org/chat") })
    val vpn = newProperty({ URL("http://localhost") })

    val news = newProperty({ URL("http://go.blokada.org/news") })
    val feedback = newProperty({ URL("http://go.blokada.org/feedback") })
    val patron = newProperty({ URL("http://go.blokada.org/patron_redirect") })
    val obsolete = newProperty({ URL("https://blokada.org/api/legacy/content/en/obsolete.html") })
    val download = newProperty({ URL("https://blokada.org/#download") })

}

private fun resolveRedirect(url: URL): URL {
    return try {
        val ucon = url.openConnection() as HttpURLConnection
        ucon.setInstanceFollowRedirects(false)
        URL(ucon.getHeaderField("Location"))
    } catch (e: Exception) {
        url
    }
}
