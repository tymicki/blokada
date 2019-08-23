package core

import android.view.ViewGroup
import jp.wasabeef.blurry.Blurry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModalManager {

    private var blurOpen = false

    suspend fun openModal() = withContext(Dispatchers.Main.immediate) {
        getActivityParentView()?.apply {
            if (this is ViewGroup) {
                Blurry.with(context).animate(500).onto(this)
                blurOpen = true
            }
        }
    }

    suspend fun closeModal() = withContext(Dispatchers.Main.immediate) {
        if (blurOpen)
            getActivityParentView()?.apply {
                if (this is ViewGroup) {
                    val last = childCount - 1
                    removeViewAt(last)
                    blurOpen = false
                }
            }
    }
}
