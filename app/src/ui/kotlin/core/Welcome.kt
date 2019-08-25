package core

import g11n.i18n
import gs.environment.Worker
import gs.property.kctx
import gs.property.newPersistedProperty2
import gs.property.newProperty

val welcome by lazy {
    WelcomeImpl(kctx)
}

class WelcomeImpl (
        w: Worker
) {
    val introSeen = newPersistedProperty2(w, "intro_seen", { false })
    val guideSeen = newPersistedProperty2(w, "guide_seen", { false })
    val patronShow = newProperty(w, { false })
    val patronSeen = newPersistedProperty2(w, "optional_seen", { false })
    val ctaSeenCounter = newPersistedProperty2(w, "cta_seen", { 3 })
    val advanced = newPersistedProperty2(w, "advanced", { false })
    val conflictingBuilds = newProperty(w, { listOf<String>() })

    init {
        i18n.locale.doWhenSet().then {
            patronShow %= true
        }

        conflictingBuilds %= listOf("org.blokada.origin.alarm", "org.blokada.alarm", "org.blokada", "org.blokada.dev")
    }
}


