package core

import g11n.i18n
import gs.environment.Worker
import gs.property.kctx
import gs.property.newProperty
import java.net.HttpURLConnection
import java.net.URL

val pages by lazy {
    PagesImpl(kctx)
}

class PagesImpl (
        w: Worker
) {

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

    val loaded = newProperty(w, { false })
    val intro = newProperty(w, { URL("http://localhost") })
    val updated = newProperty(w, { URL("http://localhost") })
    val patronAbout = newProperty(w, { URL("http://localhost") })
    val cleanup = newProperty(w, { URL("http://localhost") })
    val cta = newProperty(w, { URL("http://localhost") })
    val donate = newProperty(w, { URL("http://localhost") })
    val help = newProperty(w, { URL("http://localhost") })
    val changelog = newProperty(w, { URL("http://localhost") })
    val credits = newProperty(w, { URL("http://localhost") })
    val filters = newProperty(w, { URL("http://localhost") })
    val filtersStrings = newProperty(w, { URL("http://localhost") })
    val filtersStringsFallback = newProperty(w, { URL("http://localhost") })
    val dns = newProperty(w, { URL("http://localhost") })
    val dnsStrings = newProperty(w, { URL("http://localhost") })
    val chat = newProperty(w, { URL("http://go.blokada.org/chat") })
    val vpn = newProperty(w, { URL("http://localhost") })

    val news = newProperty(w, { URL("http://go.blokada.org/news") })
    val feedback = newProperty(w, { URL("http://go.blokada.org/feedback") })
    val patron = newProperty(w, { URL("http://go.blokada.org/patron_redirect") })
    val obsolete = newProperty(w, { URL("https://blokada.org/api/legacy/content/en/obsolete.html") })
    val download = newProperty(w, { URL("https://blokada.org/#download") })

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
