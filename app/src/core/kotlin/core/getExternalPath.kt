package core

import android.os.Environment
import java.io.File

fun getExternalPath(): String {
    var path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    path = File(path, "blokada")
    path.mkdirs()
    return path.canonicalPath
}
