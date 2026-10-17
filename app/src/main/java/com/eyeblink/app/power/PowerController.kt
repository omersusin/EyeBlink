package com.eyeblink.app.power

import android.app.Application
import android.content.SharedPreferences
import com.eyeblink.app.platform.Access
import com.eyeblink.app.ui.state.PowerUi
import com.eyeblink.core.model.OperatingMode
import com.eyeblink.core.privileged.PowerKind
import com.eyeblink.core.privileged.PowerProbe
import com.eyeblink.core.privileged.PowerStatus
import com.eyeblink.core.privileged.PrivilegedSetup
import com.eyeblink.core.privileged.ProcessShellLauncher
import com.eyeblink.core.privileged.ServiceComponents
import com.eyeblink.core.privileged.SetupStep
import com.eyeblink.core.privileged.SetupStepId
import com.eyeblink.core.privileged.ShellExecutor
import com.eyeblink.core.privileged.ShellLauncher
import com.eyeblink.core.privileged.ShellSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the Shizuku integration offers, so everything else stays free of its library. */
interface ShizukuPort {
    fun installed(): Boolean
    fun running(): Boolean
    fun granted(): Boolean

    /** Shows Shizuku's own permission dialog. */
    fun request()

    /** A launcher for a shell running as Shizuku's user (usually `shell`, uid 2000). */
    fun launcher(): ShellLauncher

    /** Calls [onChange] when Shizuku starts, stops, or answers the permission dialog. Returns a remover. */
    fun listen(onChange: () -> Unit): () -> Unit
}

/**
 * Owns the one privileged shell - Shizuku's or `su` - and says truthfully what it can do.
 *
 * The user's choice of mode is a request, not a fact: Shizuku may not be running, root may be
 * missing or denied. Whatever is chosen, the status shown is what a probe of the real shell
 * measured, and when the shell is not there EyeBlink simply carries on rootless.
 */
class PowerController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val shizuku: ShizukuPort?,
    private val prefs: SharedPreferences,
    private val lsposedActive: () -> Boolean,
    private val onChange: () -> Unit,
) {
    @Volatile var ui: PowerUi = PowerUi()
        private set

    private var wanted = OperatingMode.ROOTLESS
    private var shell: ShellSession? = null
    private var rootChecked: Boolean? = if (prefs.contains(ROOT_OK)) prefs.getBoolean(ROOT_OK, false) else null
    private val mutex = Mutex()
    private var unlisten: (() -> Unit)? = null

    /** The shell to run privileged commands with, or null when there is none. */
    val executor: ShellExecutor? get() = shell?.takeIf { ui.status.available }

    val status: PowerStatus get() = ui.status

    fun start() {
        if (unlisten == null) unlisten = shizuku?.listen { scope.launch { reevaluate() } }
        publish()
    }

    fun stop() {
        unlisten?.invoke()
        unlisten = null
        closeShell()
    }

    fun onModeChanged(mode: OperatingMode) {
        wanted = mode
        scope.launch { reevaluate() }
    }

    fun requestShizuku() {
        shizuku?.request()
    }

    /** Asks `su` for access. Only ever called from a deliberate tap, because it shows a prompt. */
    fun detectRoot() {
        scope.launch {
            mutex.withLock {
                update { copy(busy = true) }
                val session = ShellSession(ProcessShellLauncher(listOf("su")))
                val result = PowerProbe.probe(PowerKind.ROOT, session)
                rootChecked = result.available
                prefs.edit().putBoolean(ROOT_OK, result.available).apply()
                if (wanted == OperatingMode.ROOT && result.available) adopt(session) else session.close()
                update { copy(busy = false, rootAvailable = rootChecked, status = if (wanted == OperatingMode.ROOT) result else status) }
            }
        }
    }

    fun runSetup(steps: Set<SetupStepId>) {
        scope.launch {
            mutex.withLock {
                val exec = executor
                update { copy(busy = true, setup = emptyList()) }
                val results: List<SetupStep> = if (exec == null) {
                    steps.map { SetupStep(it, false, "no Shizuku or root shell is available") }
                } else {
                    val svc = ServiceComponents(
                        app.packageName,
                        Access.listenerComponent(app).flattenToString(),
                        Access.accessibilityComponent(app).flattenToString(),
                    )
                    PrivilegedSetup(exec, svc).run(steps, ui.status.sdk)
                }
                update { copy(busy = false, setup = results) }
            }
        }
    }

    fun refresh() {
        publish()
    }

    private suspend fun reevaluate() = mutex.withLock {
        update { copy(busy = true) }
        val status = when (wanted) {
            OperatingMode.ROOTLESS -> { closeShell(); PowerStatus.None }
            OperatingMode.SHIZUKU -> openShizuku()
            OperatingMode.ROOT -> openRoot()
        }
        update { copy(busy = false, status = status, rootAvailable = rootChecked) }
    }

    private suspend fun openShizuku(): PowerStatus {
        val port = shizuku ?: return PowerStatus.unavailable(PowerKind.SHIZUKU, "this build has no Shizuku support")
        if (!port.running()) { closeShell(); return PowerStatus.unavailable(PowerKind.SHIZUKU, "Shizuku is not running") }
        if (!port.granted()) { closeShell(); return PowerStatus.unavailable(PowerKind.SHIZUKU, "permission not granted") }
        val session = ShellSession(port.launcher())
        val result = PowerProbe.probe(PowerKind.SHIZUKU, session)
        if (result.available) adopt(session) else { session.close(); closeShell() }
        return result
    }

    private suspend fun openRoot(): PowerStatus {
        if (rootChecked != true) { closeShell(); return PowerStatus.unavailable(PowerKind.ROOT, "not checked yet") }
        val session = ShellSession(ProcessShellLauncher(listOf("su")))
        val result = PowerProbe.probe(PowerKind.ROOT, session)
        if (result.available) adopt(session) else { session.close(); closeShell() }
        return result
    }

    private fun adopt(session: ShellSession) {
        if (shell !== session) shell?.close()
        shell = session
    }

    private fun closeShell() {
        shell?.close()
        shell = null
    }

    private inline fun update(f: PowerUi.() -> PowerUi) {
        ui = ui.f().refreshedFacts()
        onChange()
    }

    private fun publish() = update { this }

    private fun PowerUi.refreshedFacts() = copy(
        shizukuInstalled = shizuku?.installed() ?: false,
        shizukuRunning = shizuku?.running() ?: false,
        shizukuGranted = shizuku?.granted() ?: false,
        rootAvailable = rootChecked,
        lsposedModuleActive = lsposedActive(),
    )

    private companion object {
        const val ROOT_OK = "root_ok"
    }
}
