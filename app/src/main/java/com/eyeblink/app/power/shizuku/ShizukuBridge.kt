package com.eyeblink.app.power.shizuku

import android.content.Context
import android.content.pm.PackageManager
import com.eyeblink.app.power.ShizukuPort
import com.eyeblink.core.privileged.JvmShellProcess
import com.eyeblink.core.privileged.ShellLauncher
import com.eyeblink.core.privileged.ShellProcess
import rikka.shizuku.Shizuku

/**
 * The only file that touches the Shizuku library.
 *
 * Shizuku's public API for running a command is a bound "user service" that needs a hand-written
 * IPC interface. Its simpler `newProcess` is private since 13.1, so this reaches it by reflection -
 * which is exactly how the project's own sample apps and most Shizuku clients do it. If a future
 * Shizuku renames it, [launcher] throws, the probe reports the shell as unavailable with that
 * message, and EyeBlink carries on rootless.
 */
class ShizukuBridge(private val ctx: Context) : ShizukuPort {

    override fun installed(): Boolean = try {
        ctx.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    override fun running(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    override fun granted(): Boolean =
        running() && runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    override fun request() {
        if (running() && !runCatching { Shizuku.isPreV11() }.getOrDefault(true)) {
            runCatching { Shizuku.requestPermission(REQUEST_CODE) }
        }
    }

    override fun launcher(): ShellLauncher = ShellLauncher {
        val process = newProcess.invoke(null, arrayOf("sh"), null, null) as Process
        JvmShellProcess(process) as ShellProcess
    }

    override fun listen(onChange: () -> Unit): () -> Unit {
        val received = Shizuku.OnBinderReceivedListener { onChange() }
        val dead = Shizuku.OnBinderDeadListener { onChange() }
        val answered = Shizuku.OnRequestPermissionResultListener { _, _ -> onChange() }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(answered)
        return {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(answered)
        }
    }

    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            .apply { isAccessible = true }
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 7101
    }
}
