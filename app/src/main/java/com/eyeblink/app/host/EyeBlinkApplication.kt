package com.eyeblink.app.host

import android.app.Application
import android.content.ComponentCallbacks2
import com.eyeblink.app.camera.CameraFaceSource
import com.eyeblink.app.engine.Engine
import com.eyeblink.app.engine.EngineHolder
import com.eyeblink.app.engine.EnginePorts
import com.eyeblink.app.power.shizuku.ShizukuBridge

/**
 * Builds the one engine, with the pieces that depend on libraries only the real app carries: the
 * Compose rest scene, CameraX with MediaPipe, and Shizuku.
 */
class EyeBlinkApplication : Application(), EngineHolder {

    override val engine: Engine by lazy {
        Engine(
            this,
            EnginePorts(
                rest = { windows, strings, reduced -> ComposeRestRenderer(this, windows, strings, reduced) },
                faces = CameraFaceSource(this),
                shizuku = ShizukuBridge(this),
            ),
        )
    }

    override fun onCreate() {
        super.onCreate()
        installFonts()
        engine.attach()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The system is short of memory while this process runs in front or as a service: the camera and its face model are the
        // heaviest thing in it, and the timer works without them. (Levels between those two groups only say the screen is gone.)
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        ) engine.onLowMemory()
    }
}
