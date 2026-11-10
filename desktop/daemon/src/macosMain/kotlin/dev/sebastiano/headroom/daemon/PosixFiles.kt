@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.sebastiano.headroom.daemon

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.LOCK_EX
import platform.posix.LOCK_UN
import platform.posix.O_CREAT
import platform.posix.O_RDWR
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.flock
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.getenv
import platform.posix.getpid
import platform.posix.mkdir
import platform.posix.open
import platform.posix.remove
import platform.posix.rename
import platform.posix.write

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

    /** Creates missing parent directories, then writes via a per-process temp file and rename. */
    public fun writeText(path: String, contents: String): Boolean {
        parent(path)?.let(::mkdirs)
        val temp = tempPath(path)
        val file = fopen(temp, "w") ?: return false
        try {
            fputs(contents, file)
        } finally {
            fclose(file)
        }
        return publish(temp, path)
    }

    /**
     * Like [writeText], but the file is readable only by this user from the first byte: the temp
     * file is created with mode 0600, so no window exists where another local user could open it.
     */
    public fun writePrivateText(path: String, contents: String): Boolean {
        parent(path)?.let(::mkdirs)
        val temp = tempPath(path)
        val descriptor = open(temp, O_WRONLY or O_CREAT or O_TRUNC, OWNER_ONLY)
        if (descriptor < 0) return false
        val bytes = contents.encodeToByteArray()
        var written = 0
        bytes.usePinned { pinned ->
            while (written < bytes.size) {
                val count =
                    write(descriptor, pinned.addressOf(written), (bytes.size - written).convert())
                if (count <= 0) {
                    close(descriptor)
                    remove(temp)
                    return false
                }
                written += count.toInt()
            }
        }
        close(descriptor)
        return publish(temp, path)
    }

    /** Runs [block] holding an exclusive advisory lock on [path], creating it if needed. */
    public fun <T> withLock(path: String, block: () -> T): T {
        val descriptor = open(path, O_RDWR or O_CREAT, OWNER_ONLY)
        check(descriptor >= 0) { "cannot open $path" }
        flock(descriptor, LOCK_EX)
        try {
            return block()
        } finally {
            flock(descriptor, LOCK_UN)
            close(descriptor)
        }
    }

    private fun tempPath(path: String): String = "$path.headroom-tmp.${getpid()}"

    private fun publish(temp: String, path: String): Boolean =
        if (rename(temp, path) == 0) {
            true
        } else {
            remove(temp)
            false
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
    private const val OWNER_ONLY: Int = 384 // 0600
}
