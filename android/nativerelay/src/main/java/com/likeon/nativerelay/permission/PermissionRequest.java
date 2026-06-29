/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.permission;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import com.likeon.nativerelay.RelayResult;
import com.likeon.nativerelay.internal.ProxyFlow;
import com.likeon.nativerelay.ui.NativeRelayProxyActivity;

/**
 * Capability: request a runtime permission. payload = camera / microphone / location / photos /
 * notification. Returns 1=granted, 0=denied. Uses the transparent proxy Activity (no Unity Activity
 * needed), so it stays a clean-layer capability.
 */
public final class PermissionRequest {

    private PermissionRequest() {}

    private static final long TIMEOUT_SECONDS = 120L; // the user may sit on the system dialog

    public static RelayResult request(Context ctx, String permKey) throws InterruptedException {
        // Notifications have no runtime permission before API 33.
        if ("notification".equals(permKey) && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return RelayResult.of(1, "granted (no runtime permission pre-33)");
        }

        String perm = mapPermission(permKey);
        if (perm == null) {
            return RelayResult.of(0, "unknown permission: " + permKey);
        }
        if (ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
            return RelayResult.of(1, "granted");
        }

        return ProxyFlow.run(ctx, NativeRelayProxyActivity.ACTION_PERMISSION, perm, TIMEOUT_SECONDS);
    }

    private static String mapPermission(String key) {
        if (key == null) {
            return null;
        }
        switch (key) {
            case "camera":       return "android.permission.CAMERA";
            case "microphone":   return "android.permission.RECORD_AUDIO";
            case "location":     return "android.permission.ACCESS_FINE_LOCATION";
            case "photos":
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? "android.permission.READ_MEDIA_IMAGES"
                        : "android.permission.READ_EXTERNAL_STORAGE";
            case "notification": return "android.permission.POST_NOTIFICATIONS"; // API 33+
            default:             return null;
        }
    }
}
