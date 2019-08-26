package gs.property

val version by lazy { VersionImpl() }

class VersionImpl {
    val appName = newProperty({ "gs" })
    val name = newProperty({ "0.0" })
    val obsolete = newProperty({ false })
}
