/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.haptic;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.likeon.nativerelay.RelayResult;

/** Clean-layer capability: vibrate. payload = light / medium / heavy / "ms:<n>". Needs the
 *  VIBRATE permission (declared in the manifest; granted at install). */
public final class Haptics {

    private Haptics() {}

    public static RelayResult vibrate(Context ctx, String payload) {
        Vibrator vibrator = resolveVibrator(ctx);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return RelayResult.of(0, "no vibrator");
        }

        long ms = durationFor(payload);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
        return RelayResult.ok(null);
    }

    private static Vibrator resolveVibrator(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm != null ? vm.getDefaultVibrator() : null;
        }
        return (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private static long durationFor(String payload) {
        if (payload == null || payload.isEmpty()) return 20L;
        if (payload.startsWith("ms:")) {
            try {
                return Math.max(1L, Long.parseLong(payload.substring(3).trim()));
            } catch (NumberFormatException e) {
                return 20L;
            }
        }
        switch (payload) {
            case "light":  return 20L;
            case "medium": return 40L;
            case "heavy":  return 60L;
            default:       return 20L;
        }
    }
}
