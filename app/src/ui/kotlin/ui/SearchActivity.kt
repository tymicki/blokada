package ui

import android.app.Activity
import android.os.Bundle
import core.VBStepView
import org.blokada.R
import ui.bits.EnterSearchVB


class SearchActivity : Activity(){

    private val stepView by lazy { findViewById<VBStepView>(R.id.view) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.vbstepview)

        val searchVB = EnterSearchVB { s -> callback(s); finish() }

        stepView.pages = listOf(
                searchVB
        )
    }

    companion object{
        private var callback : (String) -> Unit = {}

        fun setCallback(c:(String) -> Unit){
            callback = c
        }
    }
}
