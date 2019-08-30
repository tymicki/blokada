package ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.widget.FrameLayout
import android.widget.TextView
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import blocka.BLOCKA_CONFIG
import blocka.blokadaUserAgent
import blocka.checkAccountInfo
import core.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.blokada.R
import tunnel.BlockaConfig
import tunnel.showSnack
import java.net.URL


class SubscriptionActivity : Activity() {

    private val container by lazy { findViewById<FrameLayout>(R.id.view) }
    private val close by lazy { findViewById<TextView>(R.id.close) }
    private val openBrowser by lazy { findViewById<android.view.View>(R.id.browser) }

    private val subscriptionUrl by lazy {
        val cfg = get(BlockaConfig::class.java)
        newProperty({ URL("https://app.blokada.org/activate/${cfg.accountId}") })
    }

    private val dash by lazy {
        WebDash(subscriptionUrl, reloadOnError = true,
                javascript = true, forceEmbedded = false, big = true,
                onLoadSpecificUrl = "app.blokada.org/success" to {
                    this@SubscriptionActivity.finish()
                    GlobalScope.launch { showSnack(R.string.subscription_success) }
                    Unit
                })
    }

    private var view: android.view.View? = null
    private var listener: IWhen? = null
    private var exitedToBrowser = false

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.subscription_container)

        if (bound || bindChromeTabs()) {
            val url = subscriptionUrl().toExternalForm() + "?user-agent=" + blokadaUserAgent(this, true)
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()

            customTabsIntent.launchUrl(this, Uri.parse(url))
            unbindService(connection)
            finish()
        } else {
            view = dash.createView(this, container)
            listener = subscriptionUrl.doOnUiWhenSet().then {
                view?.run { dash.attach(this) }
            }
            container.addView(view)
            close.setOnClickListener { finish() }
            openBrowser.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.data = Uri.parse(subscriptionUrl().toString())
                    startActivity(intent)
                    exitedToBrowser = true
                } catch (e: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        view?.run { dash.detach(this) }
        container.removeAllViews()
        subscriptionUrl.cancel(listener)
        GlobalScope.launch { modalManager.closeModal() }
    }

    override fun onStart() {
        super.onStart()
        if (exitedToBrowser) {
            exitedToBrowser = false
            finish()
        }
    }

    override fun onStop() {
        super.onStop()

        GlobalScope.async {
            core.getMostRecent(BLOCKA_CONFIG)?.run {
                delay(3000)
                v("check account after coming back to SubscriptionActivity")
                checkAccountInfo(this)
            }
        }
    }

    override fun onBackPressed() {
//        if (!dashboardView.handleBackPressed()) super.onBackPressed()
        super.onBackPressed()
    }

    private val CUSTOM_TAB_PACKAGE_NAME = "com.android.chrome"
    private var bound = false

    var connection: CustomTabsServiceConnection = object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bound = false
        }
    }
    fun bindChromeTabs() = CustomTabsClient.bindCustomTabsService(this, CUSTOM_TAB_PACKAGE_NAME, connection)

}
