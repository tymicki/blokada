package core

import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class RegisterTest {
    @Test fun register_basics() {
        runBlocking {
            Register.sourceFor(Int::class.java, object : Source<Int> {
                private var persistence: Int = 0
                override fun <T> get(classOfT: Class<T>, id: String?): T? {
                    Assert.assertEquals(Int::class.java, classOfT)
                    return persistence as T
                }

                override fun <T> get(id: String?): T? {
                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                }

                override fun set(value: Int, id: String?) {
                    persistence = value
                }

            })
            val beforeSet = Register.get(Int::class.java)
            Register.set(Int::class.java, 11)
            val afterSet = Register.get(Int::class.java)
            val afterSet2 = Register.get(Int::class.java)

            Assert.assertEquals(0, beforeSet)
            Assert.assertEquals(11, afterSet)
            Assert.assertEquals(11, afterSet2)
        }
    }
}
