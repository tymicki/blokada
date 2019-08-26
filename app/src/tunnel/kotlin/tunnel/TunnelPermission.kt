package tunnel

import android.app.Activity
import android.net.VpnService
import core.getApplicationContext
import core.v
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

private var deferred = CompletableDeferred<Boolean>()

fun askTunnelPermission(act: Activity) = {
    v("asking for tunnel permissions")
    deferred.completeExceptionally(Exception("new permission request"))
    deferred = CompletableDeferred()
    val intent = VpnService.prepare(act)
    when (intent) {
        null -> deferred.complete(true)
        else -> act.startActivityForResult(intent, 0)
    }
    deferred
}()

fun tunnelPermissionResult(code: Int) = {
    v("received tunnel permissions response", code)
    when {
        deferred.isCompleted -> Unit
        code == -1 -> deferred.complete(true)
        else -> deferred.completeExceptionally(Exception("permission result: $code"))
    }
}()

fun checkTunnelPermissions() {
    val ctx = runBlocking { getApplicationContext()!! }
    if (VpnService.prepare(ctx) != null) {
        throw Exception("no tunnel permissions")
    }
}
