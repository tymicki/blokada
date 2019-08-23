package core

import android.view.ViewGroup
import jp.wasabeef.blurry.Blurry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.blokada.R

class ModalManager {

    private var blurOpen = false

    suspend fun openModal() = withContext(Dispatchers.Main.immediate) {
        val view = getActivity()?.findViewById<ViewGroup>(R.id.root)
        view?.apply {
            Blurry.with(context).animate(500).onto(this)
            blurOpen = true
        }
    }

    suspend fun closeModal() = withContext(Dispatchers.Main.immediate) {
        if (blurOpen) {
            val view = getActivity()?.findViewById<ViewGroup>(R.id.root)
            view?.apply {
                val last = childCount - 1
                removeViewAt(last)
                blurOpen = false
            }
        }
    }
}
