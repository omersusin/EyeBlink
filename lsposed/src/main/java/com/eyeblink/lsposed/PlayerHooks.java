package com.eyeblink.lsposed;

import android.app.Application;
import android.media.MediaPlayer;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Watches the players an app creates and starts listening for EyeBlink once the app has a context.
 * Hooking is limited to object creation: nothing about how the app plays, loads or shows content is
 * changed, and the players are only ever touched when EyeBlink asks.
 */
final class PlayerHooks {

    private static final String[] EXOPLAYER_CLASSES = {
        "androidx.media3.exoplayer.ExoPlayerImpl",
        "com.google.android.exoplayer2.ExoPlayerImpl",
        "com.google.android.exoplayer2.SimpleExoPlayer",
    };

    static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        XC_MethodHook track = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Players.track(param.thisObject);
            }
        };

        XposedBridge.hookAllConstructors(MediaPlayer.class, track);
        for (String name : EXOPLAYER_CLASSES) {
            Class<?> c = XposedHelpers.findClassIfExists(name, lpparam.classLoader);
            if (c != null) XposedBridge.hookAllConstructors(c, track);
        }

        // The receiver needs a Context; the application object is the first one the app has.
        XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Bridge.register((Application) param.thisObject);
                } catch (Throwable t) {
                    XposedBridge.log("EyeBlink: bridge could not start: " + t);
                }
            }
        });
    }

    private PlayerHooks() {}
}
