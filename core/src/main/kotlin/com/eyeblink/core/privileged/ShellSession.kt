package com.eyeblink.core.privileged

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

data class ShellResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false,
    /** The shell could not be started or died mid-command. */
    val failedToRun: Boolean = false,
) {
    val ok: Boolean get() = exitCode == 0 && !timedOut && !failedToRun

    companion object {
        fun failure(reason: String) = ShellResult(-1, reason, failedToRun = true)
    }
}

/** Anything that can run one command and report back. Faked in tests of the layers above. */
fun interface ShellExecutor {
    suspend fun run(command: String, timeoutMs: Long): ShellResult
}

suspend fun ShellExecutor.run(command: String): ShellResult = run(command, ShellSession.DEFAULT_TIMEOUT_MS)

/** A running shell process: Root (`su`), Shizuku (`sh` as the shell user), or `/bin/sh` in tests. */
interface ShellProcess : AutoCloseable {
    val stdin: OutputStream
    val stdout: InputStream
    fun isAlive(): Boolean
    fun destroy()
    override fun close() = destroy()
}

fun interface ShellLauncher {
    /** Starts a shell. Throws on failure (for example `su` not being installed). */
    fun launch(): ShellProcess
}

/** A shell backed by a plain [Process]. `su` and `/bin/sh` both use this. */
class JvmShellProcess(private val process: Process) : ShellProcess {
    override val stdin: OutputStream get() = process.outputStream
    override val stdout: InputStream get() = process.inputStream
    override fun isAlive(): Boolean = process.isAlive
    override fun destroy() {
        runCatching { process.destroyForcibly() }
    }
}

class ProcessShellLauncher(private val command: List<String>) : ShellLauncher {
    override fun launch(): ShellProcess {
        val p = ProcessBuilder(command).redirectErrorStream(true).start()
        return JvmShellProcess(p)
    }
}

/**
 * One persistent privileged shell, used command by command.
 *
 * Each command runs in a subshell with stdin closed, stderr merged into stdout, followed by a
 * line carrying a per-call random marker and the exit status. Reading stops at the marker, so
 * output that happens to look like a marker, a command that calls `exit`, or one that tries to
 * read stdin cannot desynchronise the session. Commands are serialised.
 *
 * A command that does not finish within its timeout kills the process; the next call starts a
 * fresh one. Nothing here ever blocks the caller beyond that timeout.
 */
class ShellSession(
    private val launcher: ShellLauncher,
    private val nonce: () -> String = { UUID.randomUUID().toString().replace("-", "").take(16) },
) : ShellExecutor, AutoCloseable {

    private val mutex = Mutex()
    private var process: ShellProcess? = null

    val isOpen: Boolean get() = process?.isAlive() == true

    override suspend fun run(command: String, timeoutMs: Long): ShellResult = mutex.withLock {
        val proc = try {
            process?.takeIf { it.isAlive() } ?: launcher.launch().also { process = it }
        } catch (e: Exception) {
            process = null
            return ShellResult.failure(e.message ?: "could not start shell")
        }

        val marker = "__EB_${nonce()}_"
        val framed = "( $command ) </dev/null 2>&1\nprintf '\\n$marker%d__\\n' \$?\n"
        var timedOut = false

        val result: ShellResult? = coroutineScope {
            val watchdog = launch {
                delay(timeoutMs)
                timedOut = true
                proc.destroy()
            }
            val r = withContext(Dispatchers.IO) { exchange(proc, framed, marker) }
            watchdog.cancel()
            r
        }

        if (result == null || timedOut) {
            dropProcess()
            return if (timedOut) ShellResult(-1, "", timedOut = true) else ShellResult.failure("shell closed unexpectedly")
        }
        result
    }

    private fun exchange(proc: ShellProcess, framed: String, marker: String): ShellResult? {
        try {
            proc.stdin.write(framed.toByteArray(Charsets.UTF_8))
            proc.stdin.flush()
        } catch (e: IOException) {
            return null
        }

        val buf = ByteArray(8 * 1024)
        val text = StringBuilder()
        val pattern = Regex(Regex.escape(marker) + "(-?\\d+)__")
        while (true) {
            val n = try {
                proc.stdout.read(buf)
            } catch (e: IOException) {
                -1
            }
            if (n < 0) return null
            if (text.length < MAX_OUTPUT_CHARS) text.append(String(buf, 0, n, Charsets.UTF_8))
            val m = pattern.find(text) ?: continue
            val body = text.substring(0, m.range.first)
            // Like command substitution: the framing newline we add, and the trailing newlines
            // commands naturally print, are not part of the answer.
            return ShellResult(m.groupValues[1].toInt(), body.trimEnd('\n'))
        }
    }

    private fun dropProcess() {
        process?.destroy()
        process = null
    }

    override fun close() {
        dropProcess()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 6_000L
        const val MAX_OUTPUT_CHARS = 1_500_000
    }
}
