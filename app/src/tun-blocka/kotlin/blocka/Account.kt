package blocka

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.cloudflare.app.boringtun.BoringTunJNI
import core.*
import kotlinx.coroutines.*
import org.blokada.R
import retrofit2.Call
import retrofit2.Response
import tunnel.BlockaConfig
import tunnel.showSnack
import ui.displayAccountExpiredNotification
import ui.displayLeaseExpiredNotification
import java.util.*

val MAX_RETRIES = 3
val EXPIRATION_OFFSET = 60 * 1000

// To prevent request loop
private var requestsSince = 0L
private var requests = 0

private fun scheduleRecheck(config: BlockaConfig) {
    val ctx = runBlocking { getApplicationContext()!! }
    val alarm: AlarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val operation = Intent(ctx, RenewLicenseReceiver::class.java).let { intent ->
        PendingIntent.getBroadcast(ctx, 0, intent, 0)
    }

    val accountTime = config.getAccountExpiration()
    val leaseTime = config.getLeaseExpiration()
    val sooner = if (accountTime.before(leaseTime)) accountTime else leaseTime
    if (sooner.before(Date())) {
        emit(BLOCKA_CONFIG, config.copy(blockaVpn = false))
        GlobalScope.async {
            // Wait until tunnel is off and recheck
            delay(3000)
            checkAccountInfo(config)
        }
    } else {
        alarm.set(AlarmManager.RTC, sooner.time, operation)
        v("scheduled account / lease recheck for $sooner")
    }
}

private fun newLease(config: BlockaConfig, retry: Int = 0) {
    v("new lease api call")

    restApi.newLease(RestModel.LeaseRequest(config.accountId, config.publicKey, config.gatewayId,
            alias = "%s-%s".format(Build.MANUFACTURER, Build.DEVICE)))
           .enqueue(object: retrofit2.Callback<RestModel.Lease> {
        override fun onFailure(call: Call<RestModel.Lease>?, t: Throwable?) {
            e("new lease api call error", t ?: "null")
            if (retry < MAX_RETRIES) newLease(config, retry + 1)
            else clearConnectedGateway(config)
        }

        override fun onResponse(call: Call<RestModel.Lease>?, response: Response<RestModel.Lease>?) {
            response?.run {
                when (code()) {
                    200 -> {
                        body()?.run {
                            val newCfg = config.copy(
                                    vip4 = lease.vip4,
                                    vip6 = lease.vip6,
                                    leaseActiveUntil = lease.expires,
                                    blockaVpn = true
                            )
                            v("new active lease, until: ${lease.expires}")
                            emit(BLOCKA_CONFIG, newCfg)
                            scheduleRecheck(newCfg)
                        }
                    }
                    403 -> {
                        e("new lease api call response 403 - too many devices")
                        clearConnectedGateway(config, tooManyDevices = true, showError = true)
                        Unit
                    }
                    else -> {
                        e("new lease api call response ${code()}")
                        if (retry < MAX_RETRIES) newLease(config, retry + 1)
                        else clearConnectedGateway(config)
                        Unit
                    }
                }
            }
        }
    })
}

fun deleteLease(config: BlockaConfig, retry: Int = 0) {
    if (config.gatewayId.isBlank()) return

    // TODO: rewrite it to sync version, or use callback on finish
    restApi.deleteLease(RestModel.LeaseRequest(config.accountId, config.publicKey, config.gatewayId, "")).enqueue(object: retrofit2.Callback<Void> {

        override fun onFailure(call: Call<Void>?, t: Throwable?) {
            e("delete lease api call error", t ?: "null")
            if (retry < MAX_RETRIES) deleteLease(config, retry + 1)
        }

        override fun onResponse(call: Call<Void>?, response: Response<Void>?) {
            e("obsolete lease deleted")
        }

    })
}

private fun checkLease(config: BlockaConfig, retry: Int = 0) {
    v("check lease api call")
    restApi.getLeases(config.accountId).enqueue(object: retrofit2.Callback<RestModel.Leases> {
        override fun onFailure(call: Call<RestModel.Leases>?, t: Throwable?) {
            e("leases api call error", t ?: "null")
            if (retry < MAX_RETRIES) checkLease(config, retry + 1)
            else clearConnectedGateway(config)
        }

        override fun onResponse(call: Call<RestModel.Leases>?, response: Response<RestModel.Leases>?) {
            response?.run {
                when (code()) {
                    200 -> {
                        body()?.run {
//                            // User might have a lease for old private key (if restoring account)
//                            val obsoleteLeases = leases.filter { it.publicKey != config.publicKey }
//                            obsoleteLeases.forEach {
//                                deleteLease(config.copy(
//                                        publicKey = it.publicKey,
//                                        gatewayId = it.gatewayId
//                                ))
//                            }
//                            if (obsoleteLeases.isNotEmpty()) GlobalScope.launch { showSnack(R.string.slot_lease_deleted_information) }

                            val lease = leases.firstOrNull {
                                it.publicKey == config.publicKey && it.gatewayId == config.gatewayId
                            }
                            if (lease != null && !lease.expiresSoon()) {
                                val newCfg = config.copy(
                                        vip4 = lease.vip4,
                                        vip6 = lease.vip6,
                                        leaseActiveUntil = lease.expires,
                                        blockaVpn = true
                                )
                                v("found active lease until: ${lease.expires}")
                                emit(BLOCKA_CONFIG, newCfg)
                                scheduleRecheck(newCfg)
                            } else {
                                v("no active lease, or expires soon")
                                newLease(config)
                            }
                        }
                    }
                    else -> {
                        e("leases api call response ${code()}")
                        if (retry < MAX_RETRIES) checkLease(config, retry + 1)
                        else clearConnectedGateway(config)
                        Unit
                    }
                }
            }
        }
    })
}

fun checkLeaseIfNeeded() {
    GlobalScope.async {
        getMostRecent(BLOCKA_CONFIG)?.run {
            if (leaseActiveUntil.before(Date())) checkLease(this)
        }
    }
}

fun checkGateways(config: BlockaConfig, retry: Int = 0) {
    v("check gateway api call")
    restApi.getGateways().enqueue(object: retrofit2.Callback<RestModel.Gateways> {
        override fun onFailure(call: Call<RestModel.Gateways>?, t: Throwable?) {
            e("gateways api call error", t ?: "null")
            if (retry < MAX_RETRIES) checkGateways(config, retry + 1)
            else clearConnectedGateway(config)
        }

        override fun onResponse(call: Call<RestModel.Gateways>?, response: Response<RestModel.Gateways>?) {
            response?.run {
                when (code()) {
                    200 -> {
                        body()?.run {
                            val gateway = gateways.firstOrNull { it.publicKey == config.gatewayId }
                            if (gateway != null) {
                                val newCfg = config.copy(
                                        gatewayId = gateway.publicKey,
                                        gatewayIp = gateway.ipv4,
                                        gatewayPort = gateway.port,
                                        gatewayNiceName = gateway.niceName()
                                )
                                v("found gateway, chosen: ${newCfg.gatewayId}")
                                checkLease(newCfg)
                            } else {
                                v("found no matching gateway")
                                clearConnectedGateway(config)
                            }
                        }
                    }
                    else -> {
                        e("gateways api call response ${code()}")
                        if (retry < MAX_RETRIES) checkGateways(config, retry + 1)
                        else clearConnectedGateway(config)
                        Unit
                    }
                }
            }
        }
    })
}

fun clearConnectedGateway(config: BlockaConfig, showError: Boolean = true, tooManyDevices: Boolean = false) {
    v("clearing connected gateway")
    if (showError && tooManyDevices) {
        GlobalScope.launch { showSnack(R.string.slot_too_many_leases) }
    } else if (config.blockaVpn && showError) {
        displayLeaseExpiredNotification()
        GlobalScope.launch { showSnack(R.string.slot_lease_cant_connect) }
    }else if (config.accountId.isBlank() && showError) {
        GlobalScope.launch { showSnack(R.string.slot_account_cant_create) }
    }

    deleteLease(config)
    emit(BLOCKA_CONFIG, config.copy(
            blockaVpn = false,
            gatewayId = "",
            gatewayIp = "",
            gatewayPort = 0,
            gatewayNiceName = ""
    ))
}

fun checkAccountInfo(config: BlockaConfig, retry: Int = 0, showError: Boolean = false) {
    if (requestsSince + 10 * 1000 < System.currentTimeMillis()) {
        // 10 seconds passed, its ok to make requests
        requestsSince = System.currentTimeMillis()
        requests = 0
    }

    if (++requests > 10) {
        e("too many check account requests recently, disabling vpn")
        clearConnectedGateway(config)
        requests = 0
        return
    }

    if (config.accountId.isBlank()) {
        v("accountId not set, creating new account")
        newAccount(config)
        return
    }

    v("check account api call")

    val accountId = if (config.restoredAccountId.isBlank()) config.accountId else config.restoredAccountId
    restApi.getAccountInfo(accountId).enqueue(object: retrofit2.Callback<RestModel.Account> {
        override fun onFailure(call: Call<RestModel.Account>?, t: Throwable?) {
            e("check account api call error", t ?: "null")
            if (retry < MAX_RETRIES) checkAccountInfo(config, retry + 1, showError)
            else {
                if (showError) GlobalScope.launch { showSnack(R.string.slot_account_name_api_error) }
                clearConnectedGateway(config, showError = false)
            }
        }

        override fun onResponse(call: Call<RestModel.Account>?, response: Response<RestModel.Account>?) {
            response?.run {
                when (code()) {
                    200 -> {
                        body()?.run {
                            val newCfg = if (config.restoredAccountId.isBlank()) config.copy(
                                    activeUntil = account.activeUntil
                            ) else {
                                v("restored account id")
                                config.copy(
                                    activeUntil = account.activeUntil,
                                    accountId = config.restoredAccountId,
                                    restoredAccountId = ""
                                )
                            }
                            if (!account.expiresSoon()) {
                                v("current account active until: ${newCfg.activeUntil}")
                                checkGateways(newCfg)
                            } else {
                                v("current account inactive")
                                if (newCfg.blockaVpn) {
                                    displayAccountExpiredNotification()
                                    GlobalScope.launch { showSnack(R.string.account_inactive) }
                                }
                                clearConnectedGateway(newCfg, showError = false)
                            }
                        }
                    }
                    else -> {
                        e("check account api call response ${code()}")
                        if (retry < MAX_RETRIES) checkAccountInfo(config, retry + 1, showError)
                        else {
                            if (showError) GlobalScope.launch { showSnack(R.string.slot_account_name_api_error) }
                            clearConnectedGateway(config, showError = false)
                        }
                        Unit
                    }
                }
            }
        }
    })
}

private fun newAccount(config: BlockaConfig, retry: Int = 0) {
    restApi.newAccount().enqueue(object: retrofit2.Callback<RestModel.Account> {
        override fun onFailure(call: Call<RestModel.Account>?, t: Throwable?) {
            e("new account api call error", t ?: "null")
            if (retry < MAX_RETRIES) newAccount(config, retry + 1)
            else clearConnectedGateway(config)
        }

        override fun onResponse(call: Call<RestModel.Account>?, response: Response<RestModel.Account>?) {
            response?.run {
                when (code()) {
                    200 -> {
                        body()?.run {
                            val secret = BoringTunJNI.x25519_secret_key()
                            val public = BoringTunJNI.x25519_public_key(secret)
                            val newCfg = config.copy(
                                    accountId = account.accountId,
                                    privateKey = BoringTunJNI.x25519_key_to_base64(secret),
                                    publicKey = BoringTunJNI.x25519_key_to_base64(public)
                            )
                            emit(BLOCKA_CONFIG, newCfg)
                            v("new user. public key: ${newCfg.publicKey}")
                        }
                    }
                    else -> {
                        e("new account api call response ${code()}")
                        if (retry < MAX_RETRIES) newAccount(config, retry + 1)
                        else clearConnectedGateway(config)
                    }
                }
            }
        }
    })
}

