package core

open class Kontext internal constructor(
        private val id: Any
) {

    companion object {
        fun new(id: Any) = Kontext(id)
    }
}

fun String.ktx() = Kontext.new(this)
