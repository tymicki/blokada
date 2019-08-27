package core

// TODO: move it somewhere or delete

/**
 * Models used for persistence in 3.5.
 */

@Deprecated("Legacy import only")
data class FiltersCache(
        val cache: Set<Filter> = emptySet()
)

@Deprecated("Legacy import only")
typealias FilterId = String

@Deprecated("Legacy import only")
data class Filter(
        val id: FilterId,
        val source: FilterSourceDescriptor,
        val whitelist: Boolean = false,
        val active: Boolean = false,
        val hidden: Boolean = false,
        val priority: Int = 0,
        val credit: String? = null,
        val customName: String? = null,
        val customComment: String? = null
)

/**
 * Models used for persistence in 3.5.
 */

@Deprecated("Legacy import only")
class FilterSourceDescriptor(
        val id: String,
        val source: String
)
