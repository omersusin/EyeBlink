package com.eyeblink.app.engine

import android.content.Context

/** Implemented by the Application so services and the activity can reach the one engine. */
interface EngineHolder {
    val engine: Engine
}

val Context.engine: Engine get() = (applicationContext as EngineHolder).engine
