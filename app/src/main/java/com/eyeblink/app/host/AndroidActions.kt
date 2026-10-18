package com.eyeblink.app.host

import com.eyeblink.app.engine.engine
import com.eyeblink.app.platform.Access
import com.eyeblink.app.ui.state.PermissionKind
import com.eyeblink.app.ui.state.UiActions
import com.eyeblink.core.model.EyeBlinkSettings
import com.eyeblink.core.privileged.SetupStepId

/** Everything the screens can ask for. Most of it goes straight to the engine; permissions need the activity. */
class AndroidActions(private val activity: MainActivity) : UiActions {

    private val engine = activity.engine

    override fun setRunning(on: Boolean) = engine.setRunning(on)
    override fun blinkNow() = engine.blinkNow()
    override fun restNow() = engine.restNow()
    override fun snooze(minutes: Int) = engine.snooze(minutes)
    override fun cancelSnooze() = engine.cancelSnooze()
    override fun emergencyStop() = engine.emergencyStop()

    override fun update(transform: (EyeBlinkSettings) -> EyeBlinkSettings) = engine.update(transform)

    override fun request(permission: PermissionKind) {
        when (permission) {
            PermissionKind.CAMERA -> activity.requestCamera { }
            PermissionKind.NOTIFICATIONS -> activity.requestNotifications()
            else -> Access.settingsIntent(activity, permission)?.let { activity.startActivity(it) }
        }
    }

    override fun addApp(packageName: String) = engine.addApp(packageName)
    override fun removeApp(packageName: String) = engine.removeApp(packageName)
    override fun testCompat(packageName: String) = engine.testCompat(packageName)

    override fun openApp(packageName: String) {
        activity.packageManager.getLaunchIntentForPackage(packageName)?.let { activity.startActivity(it) }
    }

    override fun startEyeTest() = activity.requestCamera { granted ->
        if (granted) engine.startEyeTest() else engine.eyeTestCameraDenied()
    }
    override fun stopEyeTest() = engine.stopEyeTest()

    override fun startCalibration() = activity.requestCamera { granted ->
        if (granted) engine.startCalibration() else engine.calibrationCameraDenied()
    }
    override fun skipCalibrationPhase() = engine.skipCalibrationPhase()
    override fun cancelCalibration() = engine.cancelCalibration()
    override fun saveCalibration() = engine.saveCalibration()
    override fun resetCalibration() = engine.resetCalibration()

    override fun requestShizuku() = engine.requestShizuku()
    override fun detectRoot() = engine.detectRoot()
    override fun runSetup(steps: Set<SetupStepId>) = engine.runSetup(steps)

    override fun openLsposed() {
        activity.packageManager.getLaunchIntentForPackage("org.lsposed.manager")?.let { activity.startActivity(it) }
    }

    override fun clearStats() = engine.clearStats()
    override fun copyReport() = engine.copyReport()
    override fun finishOnboarding() = engine.finishOnboarding()
}
