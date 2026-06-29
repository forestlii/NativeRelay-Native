/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.device;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** Clean-layer capability: device / system info as a json string. No Activity, no permission. */
public final class DeviceInfo {

    private DeviceInfo() {}

    public static String get(Context ctx) throws JSONException {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();

        long totalMemMB = 0;
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            totalMemMB = mi.totalMem / (1024L * 1024L);
        }

        JSONObject o = new JSONObject();
        o.put("model", Build.MODEL);
        o.put("manufacturer", Build.MANUFACTURER);
        o.put("os", "android");
        o.put("osVersion", Build.VERSION.RELEASE);
        o.put("sdkInt", Build.VERSION.SDK_INT);
        o.put("lang", Locale.getDefault().toLanguageTag());
        o.put("totalMemMB", totalMemMB);
        o.put("screenW", dm.widthPixels);
        o.put("screenH", dm.heightPixels);
        o.put("dpi", dm.densityDpi);
        return o.toString();
    }
}
