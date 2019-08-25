package core

import gs.environment.Worker
import gs.property.kctx
import gs.property.newPersistedProperty
import gs.property.newProperty
import kotlinx.coroutines.runBlocking
import tunnel.askTunnelPermission
import tunnel.checkTunnelPermissions
import tunnel.hasCompleted

val tunnelState by lazy {
    runBlocking {
        TunnelImpl(kctx)
    }
}

class TunnelImpl(
        kctx: Worker
) {

    private val ctx by lazy {
        runBlocking { getApplicationContext()!! }
    }

    val enabled = newPersistedProperty(kctx, APrefsPersistence(ctx, "enabled"),
            { false }
    )

    val error = newProperty(kctx, { false })

    val active = newPersistedProperty(kctx, APrefsPersistence(ctx, "active"),
            { false }
    )

    val restart = newPersistedProperty(kctx, APrefsPersistence(ctx, "restart"),
            { false }
    )

    val retries = newProperty(kctx, { 3 })

    val updating = newProperty(kctx, { false })

    val tunnelState = newProperty(kctx, { TunnelState.INACTIVE })

    val tunnelPermission = newProperty(kctx, {
        val (completed, _) = hasCompleted({ checkTunnelPermissions(ctx.ktx("check perm")) })
        completed
    })

    val tunnelDropCount = newPersistedProperty(kctx, APrefsPersistence(ctx, "tunnelAdsCount"),
            { 0 }
    )

    val tunnelDropStart = newPersistedProperty(kctx, APrefsPersistence(ctx, "tunnelAdsStart"),
            { System.currentTimeMillis() }
    )

    val tunnelRecentDropped = newProperty<List<String>>(kctx, { listOf() })

    val startOnBoot  = newPersistedProperty(kctx, APrefsPersistence(ctx, "startOnBoot"),
            { true }
    )
}

val permissionAsker = object : IPermissionsAsker {
    override fun askForPermissions() {
        val act = runBlocking { getActivity() } ?: throw Exception("starting MainActivity")
        val deferred = askTunnelPermission(Kontext.new("static perm ask"), act)
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

