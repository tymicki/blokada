package ui

import android.app.Activity
import blocka.BLOCKA_CONFIG
import blocka.checkAccountInfo
import core.*
import g11n.i18n
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import org.blokada.R


class RestoreAccountActivity : Activity() {

    private val stepView by lazy { findViewById<VBStepView>(R.id.view) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.vbstepview)

        val nameVB = EnterAccountVB(accepted = {
            name = it.toLowerCase().trim()
            restoreAccountId()
        })

        stepView.pages = listOf(
                nameVB
        )
    }

    override fun onBackPressed() {
//        if (!dashboardView.handleBackPressed()) super.onBackPressed()
        super.onBackPressed()
    }


    private var name = ""

    private fun restoreAccountId() = when {
        name.isBlank() -> Unit
        else -> {
            GlobalScope.async {
                core.getMostRecent(BLOCKA_CONFIG)?.run {
                    checkAccountInfo(copy(restoredAccountId = name), showError = true)
                    finish()
                }
            }
            Unit
        }
    }

}

class EnterAccountVB(
        private val accepted: (String) -> Unit = {}
) : SlotVB(), Stepable {

    private var input = ""
    private var inputValid = false
    private val inputRegex = Regex("^[A-z0-9]+$")

    private fun validate(input: String) = when {
        !input.matches(inputRegex) -> i18n.getString(R.string.slot_account_name_error)
        else -> null
    }

    override fun attach(view: SlotView) {
        view.enableAlternativeBackground()
        view.type = Slot.Type.EDIT
        view.content = Slot.Content(i18n.getString(R.string.slot_account_name_title),
                description = i18n.getString(R.string.slot_account_name_desc),
                action1 = Slot.Action(i18n.getString(R.string.slot_account_name_restore)) {
                    if (inputValid) {
                        view.fold()
                        accepted(input)
                    }
                }
        )

        view.onInput = { it ->
            input = it
            val error = validate(it)
            inputValid = error == null
            error
        }

        view.requestFocusOnEdit()
    }

}
