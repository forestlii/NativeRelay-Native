/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.internal;

import android.content.Context;

/**
 * Supplies the application {@link Context} the clean-layer capabilities need — WITHOUT depending
 * on Unity. Capabilities take a Context as a parameter (so they stay pure and testable); this is
 * the one place that resolves a default one.
 *
 * <p>By default it grabs the process-wide application Context via reflection (no Activity, no
 * Unity). If you'd rather inject one explicitly (e.g. the binding layer passing
 * {@code UnityPlayer.currentActivity}), call {@link #set(Context)} once at startup.
 */
public final class AppContext {

    private AppContext() {}

    private static Context cached;

    /** Inject a Context (e.g. from the .unity binding layer). Optional. */
    public static synchronized void set(Context ctx) {
        cached = ctx != null ? ctx.getApplicationContext() : null;
    }

    /** The application Context, or null if it couldn't be resolved. */
    public static synchronized Context get() {
        if (cached == null) {
            cached = resolveViaReflection();
        }
        return cached;
    }

    // Process-wide application Context, the framework way, with no Unity/Activity coupling.
    private static Context resolveViaReflection() {
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app instanceof Context) return (Context) app;
        } catch (Throwable ignored) { /* fall through */ }
        try {
            Object app = Class.forName("android.app.AppGlobals")
                    .getMethod("getInitialApplication").invoke(null);
            if (app instanceof Context) return (Context) app;
        } catch (Throwable ignored) { /* fall through */ }
        return null;
    }
}
