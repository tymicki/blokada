package tunnel

import blocka.EXPIRATION_OFFSET
import core.Persistable
import java.util.*

// TODO: can be null?
data class BlockaConfig(
        val adblocking: Boolean = true,
        val blockaVpn: Boolean = false,
        val accountId: String = "",
        val restoredAccountId: String = "",
        val activeUntil: Date = Date(0),
        val leaseActiveUntil: Date = Date(0),
        val privateKey: String = "",
        val publicKey: String = "",
        val gatewayId: String = "",
        val gatewayIp: String = "",
        val gatewayPort: Int = 0,
        val gatewayNiceName: String = "",
        val vip4: String = "",
        val vip6: String = "",
        val lastDaily: Long = 0L
): Persistable {

    fun getAccountExpiration() = Date(activeUntil.time - EXPIRATION_OFFSET)
    fun getLeaseExpiration() = Date(leaseActiveUntil.time - EXPIRATION_OFFSET)

    fun hasGateway(): Boolean {
        return gatewayId.isNotBlank() && gatewayIp.isNotBlank() && gatewayPort != 0
    }

    override fun toString(): String {
        return "BlockaConfig(adblocking=$adblocking, blockaVpn=$blockaVpn, activeUntil=$activeUntil, leaseActiveUntil=$leaseActiveUntil, publicKey='$publicKey', gatewayId='$gatewayId', gatewayIp='$gatewayIp', gatewayPort=$gatewayPort, vip4='$vip4', vip6='$vip6')"
    }

    override fun key() = "blocka:config"
}
