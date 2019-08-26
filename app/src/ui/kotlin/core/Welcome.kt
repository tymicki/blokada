package core

import g11n.i18n

val welcome by lazy {
    WelcomeImpl()
}

class WelcomeImpl {
    val introSeen = newPersistedProperty2("intro_seen", { false })
    val guideSeen = newPersistedProperty2("guide_seen", { false })
    val patronShow = newProperty({ false })
    val patronSeen = newPersistedProperty2("optional_seen", { false })
    val ctaSeenCounter = newPersistedProperty2("cta_seen", { 3 })
    val advanced = newPersistedProperty2("advanced", { false })
    val conflictingBuilds = newProperty({ listOf<String>() })

    init {
        i18n.locale.doWhenSet().then {
            patronShow %= true
        }

        conflictingBuilds %= listOf("org.blokada.origin.alarm", "org.blokada.alarm", "org.blokada", "org.blokada.dev")
    }
}


