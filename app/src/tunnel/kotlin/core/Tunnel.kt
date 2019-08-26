package core

import gs.property.newPersistedProperty
import gs.property.newProperty
import kotlinx.coroutines.runBlocking
import tunnel.askTunnelPermission
import tunnel.checkTunnelPermissions
import tunnel.hasCompleted

val tunnelState by lazy {
    runBlocking {
        TunnelImpl()
    }
}

class TunnelImpl {

    private val ctx by lazy {
        runBlocking { getApplicationContext()!! }
    }

    val enabled = newPersistedProperty(APrefsPersistence(ctx, "enabled"),
            { false }
    )

    val error = newProperty({ false })

    val active = newPersistedProperty(APrefsPersistence(ctx, "active"),
            { false }
    )

    val restart = newPersistedProperty(APrefsPersistence(ctx, "restart"),
            { false }
    )

    val retries = newProperty({ 3 })

    val updating = newProperty({ false })

    val tunnelState = newProperty({ TunnelState.INACTIVE })

    val tunnelPermission = newProperty({
        val (completed, _) = hasCompleted({ checkTunnelPermissions() })
        completed
    })

    val tunnelDropCount = newPersistedProperty(APrefsPersistence(ctx, "tunnelAdsCount"),
            { 0 }
    )

    val tunnelDropStart = newPersistedProperty(APrefsPersistence(ctx, "tunnelAdsStart"),
            { System.currentTimeMillis() }
    )

    val tunnelRecentDropped = newProperty<List<String>>({ listOf() })

    val startOnBoot  = newPersistedProperty(APrefsPersistence(ctx, "startOnBoot"),
            { true }
    )
}

val permissionAsker = object : IPermissionsAsker {
    override fun askForPermissions() {
        val act = runBlocking { getActivity() } ?: throw Exception("starting MainActivity")
        val deferred = askTunnelPermission(act)
        runBlocking {
            val response = deferred.await()
            if (!response) { throw Exception("could not get tunnel permissions") }
        }
    }
}

enum class TunnelState {
    INACTIVE, ACTIVATING, ACTIVE, DEACTIVATING, DEACTIVATED
}

interface IPermissionsAsker {
    fun askForPermissions()
}

