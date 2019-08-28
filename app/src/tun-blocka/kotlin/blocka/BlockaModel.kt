package blocka

import java.util.*

/**
 * Those files cannot change their (package) name because they are persisted.
 */

data class Account(
        val id: String,
        val privateKey: String,
        val publicKey: String,
        val activeUntil: Date
)

data class Gateway(
        val id: String,
        val ip: String,
        val port: Int,
        val niceName: String
)

data class Lease(
    val activeUntil: Date,
    val vip4: String = "",
    val vip6: String = ""
)

