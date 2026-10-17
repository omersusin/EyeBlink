package com.eyeblink.lsposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Called once for every app LSPosed has been told to hook. It installs {@link PlayerHooks}, which
 * only ever watches the players the app itself creates and listens for commands from EyeBlink.
 *
 * Nothing is hooked outside the apps the user ticked in LSPosed, and nothing here reads content,
 * network traffic or user data.
 */
public final class Entry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.isFirstApplication) return;
        String pkg = lpparam.packageName;
        // Never hook the system, ourselves or the EyeBlink app: a module that touches those is asking for trouble.
        if (pkg == null || pkg.equals("android") || pkg.startsWith("com.android.") || pkg.startsWith("com.eyeblink.")) return;
        try {
            PlayerHooks.install(lpparam);
        } catch (Throwable t) {
            XposedBridge.log("EyeBlink: hooks for " + pkg + " failed: " + t);
        }
    }
}
