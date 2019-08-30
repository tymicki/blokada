package dns

import core.*
import g11n.i18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.pcap4j.packet.namednumber.UdpPort
import tunnel.TunnelConfig
import tunnel.tunnelState
import ui.pages
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.Charset
import java.util.*

val dnsManager by lazy {
    DnsImpl()
}

suspend fun initDns() = withContext(Dispatchers.Main.immediate) {
    // Reload engine in case dns selection changes
    var currentDns: DnsChoice? = null
    dnsManager.choices.doWhenSet().then {
        val newChoice = dnsManager.choices().firstOrNull { it.active }
        if (newChoice != null && newChoice != currentDns) {
            currentDns = newChoice

            if (!tunnelState.enabled()) {
            } else if (tunnelState.active()) {
                tunnelState.restart %= true
                tunnelState.active %= false
            } else {
                tunnelState.retries.refresh()
                tunnelState.restart %= false
                tunnelState.active %= true
            }
        }
    }
}

val FALLBACK_DNS = listOf(
        InetSocketAddress(InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)), 53),
        InetSocketAddress(InetAddress.getByAddress(byteArrayOf(1, 0, 0, 1)), 53)
)

val serialiser = DnsSerialiser()
val fetcher = DnsLocalisedFetcher()

class DnsImpl {

    fun hasCustomDnsSelected(checkEnabled: Boolean): Boolean {
        return choices().firstOrNull { it.id != "default" && it.active } != null && (!checkEnabled or enabled())
    }

    private val refresh = { it: List<DnsChoice> ->
        v("refresh start", pages.dns())
        var builtInDns = listOf(DnsChoice("default", emptyList(), active = false))
        builtInDns += try {
            serialiser.deserialise(loadGzip(openUrl(pages.dns(), 10000)))
        } catch (e: Exception) {
            try {
                // Try again in case it randomly failed
                Thread.sleep(3000)
                serialiser.deserialise(loadGzip(openUrl(pages.dns(), 10000)))
            } catch (e: Exception) {
                e("failed to refresh dns", e)
                emptyList<DnsChoice>()
            }
        }
        v("got ${builtInDns.size} dns server entries")

        val newDns = if (it.isEmpty()) {
            builtInDns
        } else {
            it.map { dns ->
                val new = builtInDns.find { it == dns }
                if (new != null) {
                    new.active = dns.active
                    new.servers = dns.servers
                    new
                } else dns
            }.plus(builtInDns.minus(it))
        }

        // Make sure only one is active
        val activeCount = newDns.count { it.active }
        if (activeCount != 1) {
            newDns.forEach { it.active = false }
            newDns.first().active = true
        }

        v("refresh done")
        fetcher.fetch()
        newDns
    }

    private val ctx by lazy {
        runBlocking { getApplicationContext()!! }
    }


    val choices = newPersistedProperty(DnsChoicePersistence(),
            zeroValue = { listOf() },
            refresh = refresh,
            shouldRefresh = { it.size <= 1 })

    val dnsServers = newProperty({
        val d = if (enabled()) choices().firstOrNull { it.active } else null
        if (d?.servers?.isEmpty() ?: true) getDnsServers(ctx)
        else d?.servers!!
    })

    val enabled = newPersistedProperty2("dnsEnabled", { false })

    init {
        pages.dns.doWhenSet().then {
            choices.refresh()
        }

        choices.doOnUiWhenSet().then {
            dnsServers.refresh()
        }
        enabled.doOnUiWhenChanged(withInit = true).then {
            dnsServers.refresh()
        }
        device.connected.doOnUiWhenSet().then {
            dnsServers.refresh()
        }

        dnsServers.doWhenChanged(withInit = true).then {
            val current = dnsServers()
            val cfg = get(TunnelConfig::class.java)
            if (cfg.dnsFallback && isLocalServers(current)) {
                dnsServers %= FALLBACK_DNS
                w("local DNS detected, setting CloudFlare as workaround")
            }
        }
    }

    private fun isLocalServers(servers: List<InetSocketAddress>): Boolean {
        return when {
            servers.isEmpty() -> true
            servers.first().address.isLinkLocalAddress -> true
            servers.first().address.isSiteLocalAddress -> true
            servers.first().address.isLoopbackAddress -> true
            else -> false
        }
    }
}

data class DnsChoice(
        val id: String,
        var servers: List<InetSocketAddress>,
        var active: Boolean = false,
        var ipv6: Boolean = false,
        val credit: String? = null,
        val comment: String? = null
) {
    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is DnsChoice) return false
        return id.equals(other.id)
    }
}

class DnsChoicePersistence() : PersistenceWithSerialiser<List<DnsChoice>>() {

    val p by lazy { serialiser("dns") }
    val s by lazy { DnsSerialiser() }

    override fun read(current: List<DnsChoice>): List<DnsChoice> {
        val dns =s.deserialise(p.getString("dns", "").split("^"))
        return if (dns.isNotEmpty()) dns else current
    }

    override fun write(source: List<DnsChoice>) {
        val e = p.edit()
        e.putInt("migratedVersion", 1)
        e.putString("dns",s.serialise(source).joinToString("^"))
        e.apply()
    }

}

private fun addressToIpString(it: InetSocketAddress) =
        it.hostString + ( if (it.port != 53) ":" + it.port.toString() else "" )

private fun ipStringToAddress(it: String) = {
    val hostport = it.split(':', limit = 2)
    val host = hostport[0]
    val port = ( if (hostport.size == 2) hostport[1] else "").toIntOrNull() ?: UdpPort.DOMAIN.valueAsInt()
    InetSocketAddress(InetAddress.getByName(host), port)
}()

class DnsSerialiser {
    fun serialise(dns: List<DnsChoice>): List<String> {
        var i = 0
        return dns.map {
            val active = if (it.active) "active" else "inactive"
            val ipv6 = if (it.ipv6) "ipv6" else "ipv4"
            val servers = it.servers.map { addressToIpString(it) }.joinToString(";")
            val credit = it.credit ?: ""
            val comment = it.comment ?: ""

            "${i++}\n${it.id}\n${active}\n${ipv6}\n${servers}\n${credit}\n${comment}"
        }.flatMap { it.split("\n") }
    }

    fun deserialise(source: List<String>): List<DnsChoice> {
        if (source.size <= 1) return emptyList()
        val dns = source.asSequence().batch(7).map { entry ->
            entry[0].toInt() to try {
                val id = entry[1]
                val active = entry[2] == "active"
                val ipv6 = entry[3] == "ipv6"
                val servers = entry[4].split(";").filter { it.isNotBlank() }.map { ipStringToAddress(it) }
                val credit = if (entry[5].isNotBlank()) entry[5] else null
                val comment = if (entry[6].isNotBlank()) entry[6] else null

                DnsChoice(id, servers, active, ipv6, credit, comment)
            } catch (e: Exception) {
                null
            }
        }.toList().sortedBy { it.first }.map { it.second }.filterNotNull()
        return dns
    }
}

class DnsLocalisedFetcher() {
    init {
        i18n.locale.doWhenChanged().then { fetch() }
    }

    fun fetch() {
        v("dns: fetch strings: start ${pages.dnsStrings()}")
        val prop = Properties()
        try {
            prop.load(InputStreamReader(openUrl(pages.dnsStrings(), 10000)().getInputStream(),
                    Charset.forName("UTF-8")))
            prop.stringPropertyNames().iterator().forEach {
                i18n.set("dns_$it", prop.getProperty(it))
            }
        } catch (e: Exception) {
            v("dns: fetch strings crash", e)
        }
        v("dns: fetch strings: done")
    }
}

fun printServers(s: List<InetSocketAddress>): String {
    return s.map { addressToIpString(it) }.joinToString (", ")
}

