package core

import android.app.Activity
import android.widget.FrameLayout
import android.widget.ImageView
import org.blokada.R
import java.net.URL


class WebViewActivity : Activity() {

    companion object {
        const val EXTRA_URL = "url"
    }

    private val container by lazy { findViewById<FrameLayout>(R.id.view) }
    private val close by lazy { findViewById<ImageView>(R.id.close) }

    private lateinit var url: IProperty<URL>

    private val dash by lazy {
        WebDash(url, reloadOnError = true,
                javascript = true, forceEmbedded = true, big = true)
    }

    private var view: android.view.View? = null
    private var listener: IWhen? = null

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.subscription_container)

        url = newProperty({ URL(intent.getStringExtra(EXTRA_URL)) })

        view = dash.createView(this, container)
        listener = url.doOnUiWhenSet().then {
            view?.run { dash.attach(this) }
        }
        container.addView(view)

        close.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        view?.run { dash.detach(this) }
        container.removeAllViews()
        url.cancel(listener)
    }

}
