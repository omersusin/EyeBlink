package com.eyeblink.app.host

import android.app.Application
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
}
