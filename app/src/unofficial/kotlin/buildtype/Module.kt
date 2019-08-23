package buildtype

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun initBuildType() = withContext(Dispatchers.Main.immediate) {

}
