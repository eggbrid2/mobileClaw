package com.mobileclaw.storage

import android.util.AtomicFile
import java.io.File

/**
 * Atomic UTF-8 file persistence helper.
 * Callers should still serialize multi-step read/modify/write flows at the store level.
 */
object AtomicTextFile {
    fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        val atomicFile = AtomicFile(file)
        var output = atomicFile.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (t: Throwable) {
            atomicFile.failWrite(output)
            throw t
        }
    }

    fun readOrNull(file: File): String? =
        if (file.exists()) file.readText(Charsets.UTF_8) else null
}
