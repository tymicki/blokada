package core

import android.content.Context
import com.github.salomonbrys.kodein.*
import g11n.i18n
import gs.environment.Worker
import gs.property.IProperty
import gs.property.newPersistedProperty2
import gs.property.newProperty

abstract class Welcome {
    abstract val introSeen: IProperty<Boolean>
    abstract val guideSeen: IProperty<Boolean>
    abstract val patronShow: IProperty<Boolean>
    abstract val patronSeen: IProperty<Boolean>
    abstract val ctaSeenCounter: IProperty<Int>
    abstract val advanced: IProperty<Boolean>
    abstract val conflictingBuilds: IProperty<List<String>>
}

class WelcomeImpl (
        w: Worker
) : Welcome() {
    override val introSeen = newPersistedProperty2(w, "intro_seen", { false })
    override val guideSeen = newPersistedProperty2(w, "guide_seen", { false })
    override val patronShow = newProperty(w, { false })
    override val patronSeen = newPersistedProperty2(w, "optional_seen", { false })
    override val ctaSeenCounter = newPersistedProperty2(w, "cta_seen", { 3 })
    override val advanced = newPersistedProperty2(w, "advanced", { false })
    override val conflictingBuilds = newProperty(w, { listOf<String>() })

    init {
        i18n.locale.doWhenSet().then {
            patronShow %= true
        }

        conflictingBuilds %= listOf("org.blokada.origin.alarm", "org.blokada.alarm", "org.blokada", "org.blokada.dev")
    }
}

fun newWelcomeModule(ctx: Context): Kodein.Module {
    return Kodein.Module {
        bind<Welcome>() with singleton {
            WelcomeImpl(w = with("gscore").instance(2))
        }
    }
}

