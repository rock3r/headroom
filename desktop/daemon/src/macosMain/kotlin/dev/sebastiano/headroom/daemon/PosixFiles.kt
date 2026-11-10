@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.sebastiano.headroom.daemon

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.remove
import platform.posix.rename

/** The POSIX file operations the daemon needs, on absolute paths only. */
internal object PosixFiles {
    public fun readText(path: String): String? {
        val file = fopen(path, "r") ?: return null
        return try {
            buildString {
                val buffer = ByteArray(READ_BUFFER)
                while (true) {
                    val line = fgets(buffer.refTo(0), buffer.size, file) ?: break
                    append(line.toKString())
                }
            }
        } finally {
            fclose(file)
        }
    }

    /** Creates missing parent directories, then writes via temp file and rename. */
    public fun writeText(path: String, contents: String): Boolean {
        parent(path)?.let(::mkdirs)
        val temp = "$path.headroom-tmp"
        val file = fopen(temp, "w") ?: return false
        try {
            fputs(contents, file)
        } finally {
            fclose(file)
        }
        if (rename(temp, path) != 0) {
            remove(temp)
            return false
        }
        return true
    }

    public fun env(name: String): String? = getenv(name)?.toKString()

    public fun home(): String = env("HOME") ?: "/tmp"

    private fun mkdirs(path: String) {
        var made = ""
        for (part in path.split('/').filter { it.isNotEmpty() }) {
            made += "/$part"
            mkdir(made, DIRECTORY_MODE)
        }
    }

    private fun parent(path: String): String? = path.substringBeforeLast('/', "").ifEmpty { null }

    private const val READ_BUFFER = 8192
    private const val DIRECTORY_MODE: UShort = 493u // 0755
}
