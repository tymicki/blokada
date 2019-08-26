package tunnel

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import com.github.michaelbull.result.onFailure
import core.*
import filter.sourceProvider
import gs.property.device
import gs.property.watchdog
import kotlinx.coroutines.*
import org.blokada.R
import java.io.FileDescriptor
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*

object Events {
    val RULESET_BUILDING = "RULESET_BUILDING".newEventOf<Unit>()
    val RULESET_BUILT = "RULESET_BUILT".newEventOf<Pair<Int, Int>>()
    val FILTERS_CHANGING = "FILTERS_CHANGING".newEvent()
    val FILTERS_CHANGED = "FILTERS_CHANGED".newEventOf<Collection<Filter>>()
    val REQUEST = "REQUEST".newEventOf<Request>()
    val TUNNEL_POWER_SAVING = "TUNNEL_POWER_SAVING".newEvent()
    val MEMORY_CAPACITY = "MEMORY_CAPACITY".newEventOf<Int>()
    val TUNNEL_RESTART = "TUNNEL_RESTART".newEvent()
}

val tunnelManager by lazy {
    Main(
            onVpnClose = {
                tunnelState.tunnelPermission.refresh(blocking = true)
                tunnelState.restart %= true
                tunnelState.active %= false
            },
            onVpnConfigure = { vpn ->
                val ctx = runBlocking { getApplicationContext()!! }
                vpn.setSession(ctx.getString(R.string.branding_app_name))
                        .setConfigureIntent(PendingIntent.getActivity(ctx, 1,
                                Intent(ctx, PanelActivity::class.java),
                                PendingIntent.FLAG_CANCEL_CURRENT))
            },
            onRequest = { request ->
                if (request.blocked) {
                    tunnelState.tunnelDropCount %= tunnelState.tunnelDropCount() + 1
                    val dropped = tunnelState.tunnelRecentDropped() + request.domain
                    tunnelState.tunnelRecentDropped %= dropped.takeLast(10)
                }

                Persistence.request.save(request)
            },
            doResolveFilterSource = {
                sourceProvider.from(it.source.id, it.source.source)
            },
            doProcessFetchedFilters = {
                filtersManager.apps.refresh(blocking = true)
                it.map {
                    when {
                        it.source.id != "app" -> it
                        filtersManager.apps().firstOrNull { a -> a.appId == it.source.source } == null -> {
                            it.copy(hidden = true, active = false)
                        }
                        else -> it
                    }
                }.toSet()
            }
    )
}

suspend fun initTunnel() = withContext(Dispatchers.Main.immediate) {
    val ctx = runBlocking { getApplicationContext()!! }

    var restarts = 0
    var lastRestartMillis = 0L

    dnsManager.dnsServers.doWhenChanged(withInit = true).then {
        tunnelManager.setup(dnsManager.dnsServers())
    }

    GlobalScope.async {
        core.on(BLOCKA_CONFIG) { cfg ->
            tunnelManager.setup(dnsManager.dnsServers(), cfg)

//                    if (cfg.blockaVpn && !s.enabled()) {
//                        v("auto activating on vpn gateway selected")
//                       tunnelState.enabled %= true
//                    }
        }

        core.on(Events.TUNNEL_RESTART) {
            val restartedRecently = (System.currentTimeMillis() - lastRestartMillis) < 15 * 1000
            lastRestartMillis = System.currentTimeMillis()
            if (!restartedRecently) restarts = 0
            if (restarts++ > 9 && device.watchdogOn()) {
                restarts = 0
                e("Too many tunnel restarts. Stopping...")
                tunnelState.error %= true
                tunnelState.enabled %= false
            } else w("tunnel restarted for $restarts time in a row")
        }
    }

    var oldUrl = "localhost"
    pages.filters.doWhenSet().then {
        val url = pages.filters().toExternalForm()
        if (pages.filters().host != "localhost" && url != oldUrl) {
            oldUrl = url
            tunnelManager.setUrl(url, device.onWifi())
        }
    }

    // React to user switching us off / on
    tunnelState.enabled.doWhenSet().then {
        tunnelState.restart %= tunnelState.enabled() && (tunnelState.restart() || device.isWaiting())
        tunnelState.active %= tunnelState.enabled() && !device.isWaiting()
    }

    // React to device power saving blocking our tunnel
    core.on(tunnel.Events.TUNNEL_POWER_SAVING) {
        w("power saving detected")
        ctx.startActivity(Intent(ctx, PowersaveActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // The tunnel setup routine (with permissions request)
    tunnelState.active.doWhenSet().then {
        e("enabled: ${tunnelState.enabled()}")
        if (tunnelState.enabled() &&tunnelState.active() &&tunnelState.tunnelState(TunnelState.INACTIVE)) {
            tunnelState.retries %=tunnelState.retries() - 1
            tunnelState.tunnelState %= TunnelState.ACTIVATING
            tunnelState.tunnelPermission.refresh(blocking = true)
            if (tunnelState.tunnelPermission(false)) {
                hasCompleted({
                    permissionAsker.askForPermissions()
                })
                tunnelState.tunnelPermission.refresh(blocking = true)
            }

            if (tunnelState.tunnelPermission(true)) {
                val (completed, err) = hasCompleted({ runBlocking {
                    tunnelManager.setup(dnsManager.dnsServers(), start = true).await()
                } })
                if (completed) {
                    tunnelState.tunnelState %= TunnelState.ACTIVE
                } else {
                    e("could not start tunnel", err ?: "no exception")
                }
            }

            if (!tunnelState.tunnelState(TunnelState.ACTIVE)) {
                tunnelState.tunnelState %= TunnelState.DEACTIVATING
                hasCompleted({
                    tunnelManager.stop()
                })
                tunnelState.tunnelState %= TunnelState.DEACTIVATED
            }

            tunnelState.updating %= false
        }
    }

    // Things that happen after we get everything set up nice and sweet
    var resetRetriesTask: Job? = null

    tunnelState.tunnelState.doWhenChanged().then {
        if (tunnelState.tunnelState(TunnelState.ACTIVE)) {
            // Make sure the tunnel is actually usable by checking connectivity
            if (device.screenOn()) watchdog.start()
            if (resetRetriesTask != null) resetRetriesTask?.cancel()

            // Reset retry counter in case we seem to be stable
            resetRetriesTask = GlobalScope.launch(retryKctx) {
                if (tunnelState.tunnelState(TunnelState.ACTIVE)) {
                    Thread.sleep(15 * 1000)
                    v("tunnel stable")
                    if (tunnelState.tunnelState(TunnelState.ACTIVE))tunnelState.retries.refresh()
                }
            }
        }
    }

    // Things that happen after we get the tunnel off
    tunnelState.tunnelState.doWhenChanged().then {
        if (tunnelState.tunnelState(TunnelState.DEACTIVATED)) {
            tunnelState.active %= false
            tunnelState.restart %= true
            tunnelState.tunnelState %= TunnelState.INACTIVE
            if (resetRetriesTask != null) resetRetriesTask?.cancel()

            // Monitor connectivity if disconnected, in case we can't relay on Android event
            if (tunnelState.enabled() && device.screenOn()) watchdog.start()

            // Reset retry counter after a longer break since we never give up, never surrender
            resetRetriesTask = GlobalScope.launch(retryKctx) {
                if (tunnelState.enabled() &&tunnelState.retries(0) && !tunnelState.tunnelState(TunnelState.ACTIVE)) {
                    Thread.sleep(5 * 1000)
                    if (tunnelState.enabled() && !tunnelState.tunnelState(TunnelState.ACTIVE)) {
                        v("tunnel restart after wait")
                        tunnelState.retries.refresh()
                        tunnelState.restart %= true
                        tunnelState.tunnelState %= TunnelState.INACTIVE
                    }
                }
            }
        }
    }

    // Turn off the tunnel if disabled (by user, no connectivity, or giving up on error)
    tunnelState.active.doWhenChanged().then {
        if (tunnelState.active(false)
                &&tunnelState.tunnelState(TunnelState.ACTIVE, TunnelState.ACTIVATING)) {
            watchdog.stop()
            tunnelState.tunnelState %= TunnelState.DEACTIVATING
            hasCompleted {
                tunnelManager.stop()
            }
            tunnelState.tunnelState %= TunnelState.DEACTIVATED
        }
    }

    // Auto off in case of no connectivity, and auto on once connected
    device.connected.doWhenChanged(withInit = true).then {
        when {
            !device.connected() &&tunnelState.active() -> {
                v("no connectivity, deactivating")
                tunnelState.restart %= true
                tunnelState.active %= false
            }
            device.connected() &&tunnelState.restart() && !tunnelState.updating() &&tunnelState.enabled() -> {
                v("connectivity back, activating")
                tunnelState.restart %= false
                tunnelState.error %= false
                tunnelState.active %= true
            }
            device.connected() &&tunnelState.error() && !tunnelState.updating() && !tunnelState.enabled() -> {
                v("connectivity back, auto recover from error")
                tunnelState.error %= false
                tunnelState.enabled %= true
            }
        }
    }

    // Auto restart (eg. when reconfiguring the tunnelManager, or retrying)
    tunnelState.tunnelState.doWhen {
        tunnelState.tunnelState(TunnelState.INACTIVE) &&tunnelState.enabled() &&tunnelState.restart() &&tunnelState.updating(false)
                && !device.isWaiting() &&tunnelState.retries() > 0
    }.then {
        v("tunnel auto restart")
        tunnelState.restart %= false
        tunnelState.active %= true
    }

    // Make sure watchdog is started and stopped as user wishes
    device.watchdogOn.doWhenChanged().then { when {
        device.watchdogOn() &&tunnelState.tunnelState(TunnelState.ACTIVE, TunnelState.INACTIVE) -> {
            // Flip the connected flag so we detect the change if now we're actually connected
            device.connected %= false
            watchdog.start()
        }
        device.watchdogOn(false) -> {
            watchdog.stop()
            device.connected.refresh()
            device.onWifi.refresh()
        }
    }}

    // Monitor connectivity only when user is interacting with device
    device.screenOn.doWhenChanged().then { when {
        tunnelState.enabled(false) -> Unit
        device.screenOn() &&tunnelState.tunnelState(TunnelState.ACTIVE, TunnelState.INACTIVE) -> watchdog.start()
        device.screenOn(false) -> watchdog.stop()
    }}

    tunnelState.startOnBoot {}

    device.onWifi.doWhenChanged().then {
        tunnelManager.reloadConfig(device.onWifi())
    }

    GlobalScope.async {
        registerTunnelConfigEvent()
        registerBlockaConfigEvent()

        tunnelManager.reloadConfig(device.onWifi())
    }
}

private val retryKctx = newSingleThreadContext("retry") + logCoroutineExceptions()

class Main(
        private val onVpnClose: () -> Unit,
        private val onVpnConfigure: (VpnService.Builder) -> Unit,
        private val onRequest: Callback<Request>,
        private val doResolveFilterSource: (Filter) -> IFilterSource,
        private val doProcessFetchedFilters: (Set<Filter>) -> Set<Filter>
) {

    private val forwarder = Forwarder()
    private val loopback = LinkedList<Triple<ByteArray, Int, Int>>()
    private val blockade = Blockade()
    private var currentServers = emptyList<InetSocketAddress>()
    private var filters = FilterManager(blockade = blockade, doResolveFilterSource =
    doResolveFilterSource, doProcessFetchedFilters = doProcessFetchedFilters)
    private var socketCreator = {
        w("using not protected socket")
        DatagramSocket()
    }
    private var config = TunnelConfig()
    private var blockaConfig = BlockaConfig()
    private var proxy = createProxy()
    private var tunnel = createTunnel()
    private var connector = ServiceConnector(
            onClose = onVpnClose,
            onConfigure = { tunnel -> 0L }
    )
    private var currentUrl: String = ""

    private var tunnelThread: Thread? = null
    private var fd: FileDescriptor? = null
    private var binder: ServiceBinder? = null
    private var enabled: Boolean = false

    private val CTRL = newSingleThreadContext("tunnel-ctrl") + logCoroutineExceptions()
    private var threadCounter = 0
    private var usePausedConfigurator = false

    private fun createProxy() = if (blockaConfig.blockaVpn) null
            else DnsProxy(currentServers, blockade, forwarder, loopback, doCreateSocket = socketCreator)

    private fun createTunnel() = if (blockaConfig.blockaVpn) BlockaTunnel(currentServers, config,
            blockaConfig, socketCreator, blockade)
            else DnsTunnel(proxy!!, config, forwarder, loopback)

    private val packageName by lazy {
        runBlocking { getApplicationContext()!! }.packageName
    }

    private fun createConfigurator() = when {
        usePausedConfigurator -> PausedVpnConfigurator(currentServers, filters)
        blockaConfig.blockaVpn -> BlockaVpnConfigurator(currentServers, filters, blockaConfig, packageName)
        else -> DnsVpnConfigurator(currentServers, filters, packageName)
    }

    fun setup(servers: List<InetSocketAddress>, config: BlockaConfig? = null, start: Boolean = false) = GlobalScope.async(CTRL) {
        val cfg = config ?: blockaConfig
        val processedServers = processServers(servers, cfg)
        v("setup tunnel, start = $start, enabled = $enabled", processedServers, config ?: "no blocka config")
        enabled = start or enabled
        when {
            processedServers.isEmpty() -> {
                v("empty dns servers, will disable tunnel")
                currentServers = emptyList()
                maybeStopVpn()
                maybeStopTunnelThread()
                if (start) enabled = true
            }
            isVpnOn() && currentServers == processedServers && (config == null ||
                    blockaConfig.blockaVpn == config.blockaVpn
                    && blockaConfig.gatewayId == config.gatewayId
                    && blockaConfig.adblocking == config.adblocking) -> {
                v("no changes in configuration, ignoring")
            }
            else -> {
                currentServers = processedServers
                config?.run { blockaConfig = this }
                socketCreator = {
                    val socket = DatagramSocket()
                    val protected = binder?.service?.protect(socket) ?: false
                    if (!protected) e("could not protect")
                    socket
                }
                val configurator = createConfigurator()

                connector = ServiceConnector(onVpnClose, onConfigure = { vpn ->
                    configurator.configure(vpn)
                    onVpnConfigure(vpn)
                    5000L
                })

                v("will sync filters")
                if (filters.sync()) {
                    filters.save()

                    v("will restart vpn and tunnel")
                    maybeStopTunnelThread()
                    maybeStopVpn()
                    v("done stopping vpn and tunnel")

                    if (enabled) {
                        v("will start vpn")
                        startVpn()
                        startTunnelThread()
                    }
                }
            }
        }
        Unit
    }

    private fun processServers(servers: List<InetSocketAddress>, config: BlockaConfig?) = when {
        // Dont do anything other than it used to be, in non-vpn mode
        config?.blockaVpn != true -> servers
        servers.isEmpty() || !dnsManager.hasCustomDnsSelected(checkEnabled = true) -> {
            w("no dns set, using fallback")
            FALLBACK_DNS
        }
        else -> servers
    }

    fun reloadConfig(onWifi: Boolean) = GlobalScope.async(CTRL) {
        v("reloading config")
        createComponents(onWifi)
        filters.setUrl(currentUrl)
        if (filters.sync()) {
            filters.save()
            restartTunnelThread()
        }
    }

    fun setUrl(url: String, onWifi: Boolean) = GlobalScope.async(CTRL) {
        if (url != currentUrl) {
            currentUrl = url

            val cfg = config.loadFromPersistence()
            v("setting url, firstLoad: ${cfg.firstLoad}", url)
            createComponents(onWifi)
            filters.setUrl(url)
            if (filters.sync()) {
                v("first fetch successful, unsetting firstLoad flag")
                cfg.copy(firstLoad = false).saveToPersistence()
            }
            filters.save()
            restartTunnelThread()
        } else w("ignoring setUrl, same url already set")
    }


    fun stop() = GlobalScope.async(CTRL) {
        v("stopping tunnel")
        maybeStopTunnelThread()
        maybeStopVpn()
        currentServers = emptyList()
        enabled = false
    }

    fun load() = GlobalScope.async(CTRL) {
        filters.load()
        restartTunnelThread()
    }

    fun sync(restartVpn: Boolean = false) = GlobalScope.async(CTRL) {
        v("syncing on request")
        if (filters.sync()) {
            filters.save()
            if (restartVpn) restartVpn()
            restartTunnelThread()
        }
    }

    fun findFilterBySource(source: String) = GlobalScope.async(CTRL) {
        filters.findBySource(source)
    }

    fun putFilter(filter: Filter, sync: Boolean = true) = GlobalScope.async(CTRL) {
        v("putting filter", filter.id)
        filters.put(filter)
        if (sync) sync(restartVpn = filter.source.id == "app")
    }

    fun putFilters(newFilters: Collection<Filter>) = GlobalScope.async(CTRL) {
        v("batch putting filters", newFilters.size)
        newFilters.forEach { filters.put(it) }
        if (filters.sync()) {
            filters.save()
            if (newFilters.any { it.source.id == "app" }) restartVpn()
            restartTunnelThread()
        }
    }

    fun removeFilter(filter: Filter) = GlobalScope.async(CTRL) {
        filters.remove(filter)
        if (filters.sync()) {
            filters.save()
            restartTunnelThread()
        }
    }

    fun invalidateFilters() = GlobalScope.async(CTRL) {
        v("invalidating filters")
        filters.invalidateCache()
        if(filters.sync()) {
            filters.save()
            restartTunnelThread()
        }
    }

    fun deleteAllFilters() = GlobalScope.async(CTRL) {
        filters.removeAll()
        if (filters.sync()) {
            restartTunnelThread()
        }
    }

    fun protect(socket: Socket) {
        val protected = binder?.service?.protect(socket) ?: false
        if (!protected && isVpnOn()) e("could not protect", socket)
    }

    private fun createComponents(onWifi: Boolean) {
        config = runBlocking { config.loadFromPersistence() }
        blockaConfig = runBlocking { blockaConfig.loadFromPersistence() }
        v("create components, onWifi: $onWifi, firstLoad: ${config.firstLoad}", config)
        filters = FilterManager(
                blockade = blockade,
                doResolveFilterSource = doResolveFilterSource,
                doProcessFetchedFilters = doProcessFetchedFilters,
                doValidateRulesetCache = { it ->
                    it.source.id in listOf("app")
                            || it.lastFetch + config.cacheTTL * 1000 > System.currentTimeMillis()
                            || config.wifiOnly && !onWifi && !config.firstLoad && it.source.id == "link"
                },
                doValidateFilterStoreCache = { it ->
                    it.cache.isNotEmpty()
                            && (it.lastFetch + config.cacheTTL * 1000 > System.currentTimeMillis()
                            || config.wifiOnly && !onWifi)
                }
        )
        filters.load()
    }

    private suspend fun startVpn() {
        Result.of {
            val binding = connector.bind()
            runBlocking { binding.join() }
            binder = binding.getCompleted()
            fd = binder!!.service.turnOn()
            core.on(Events.REQUEST, onRequest)
            v("vpn started")
        }.onFailure { ex ->
            e("failed starting vpn", ex)
            onVpnClose()
        }
    }

    private fun startTunnelThread() {
        proxy = createProxy()
        tunnel = createTunnel()
        val f = fd
        if (f != null) {
            tunnelThread = Thread({ tunnel.runWithRetry(f) }, "tunnel-${threadCounter++}")
            tunnelThread?.start()
            v("tunnel thread started", tunnelThread!!)
        } else w("attempting to start tunnel thread with no fd")
    }

    private fun stopTunnelThread() {
        v("stopping tunnel thread", tunnelThread!!)
        tunnel.stop()
        Result.of { tunnelThread?.interrupt() }.onFailure { ex ->
            w("could not interrupt tunnel thread", ex)
        }
        Result.of { tunnelThread?.join(5000); true }.onFailure { ex ->
            w("could not join tunnel thread", ex)
        }
        tunnelThread = null
        v("tunnel thread stopped")
    }

    private fun stopVpn() {
        core.cancel(Events.REQUEST, onRequest)
        binder?.service?.turnOff()
        connector.unbind()//.mapError { ex -> w("failed unbinding connector", ex) }
        binder = null
        fd = null
        v("vpn stopped")
    }

    private fun restartTunnelThread() {
        if (tunnelThread != null) {
            stopTunnelThread()
            startTunnelThread()
        }
    }

    private suspend fun restartVpn() {
        if (isVpnOn()) {
            stopVpn()
            startVpn()
        }
    }

    private fun maybeStopTunnelThread() = if (tunnelThread != null) {
        stopTunnelThread(); true
    } else false

    private fun maybeStopVpn() = if (isVpnOn()) {
        stopVpn(); true
    } else false

    private fun isVpnOn() = binder != null
}

fun hasCompleted(f: () -> Unit): Pair<Boolean, Exception?> {
    return try { f(); true to null } catch (e: Exception) { w(e); false to e }
}
