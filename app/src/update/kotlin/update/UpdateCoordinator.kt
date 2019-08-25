package update

import core.TunnelState
import core.tunnelState
import gs.property.IWhen
import java.net.URL

/**
 * It makes sure Blokada is inactive during update download.
 */
class UpdateCoordinator(
        private val downloader: AUpdateDownloader
) {

    private var w: IWhen? = null
    private var downloading = false

    fun start(urls: List<URL>) {
        if (downloading) return
        if (tunnelState.tunnelState(TunnelState.INACTIVE)) {
            download(urls)
        }
        else {
            core.v("UpdateCoordinator: deactivate tunnel: ${tunnelState.tunnelState()}")
           tunnelState.tunnelState.cancel(w)
            w =tunnelState.tunnelState.doOnUiWhenChanged().then {
                if (tunnelState.tunnelState(TunnelState.INACTIVE)) {
                    if (!downloading) {
                        core.v("UpdateCoordinator: tunnel deactivated")
                       tunnelState.tunnelState.cancel(w)
                        w = null
                        download(urls)
                    }
                }
            }

           tunnelState.updating %= true
           tunnelState.restart %= true
           tunnelState.active %= false
        }
    }

    private fun download(urls: List<URL>) {
        core.v("UpdateCoordinator: start download")
        downloading = true
        downloader.downloadUpdate(urls) { uri ->
            core.v("UpdateCoordinator: downloaded: url $uri")
            if (uri != null) downloader.openInstall(uri)
           tunnelState.updating %= false
            downloading = false
        }
    }

}

