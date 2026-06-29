/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.system;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.likeon.nativerelay.RelayResult;

/** Clean-layer capability: open app / notification settings. payload = app / notification /
 *  store-review. Started from a non-Activity Context, so it adds FLAG_ACTIVITY_NEW_TASK. */
public final class SettingsOpener {

    private SettingsOpener() {}

    public static RelayResult open(Context ctx, String payload) {
        String target = (payload == null || payload.isEmpty()) ? "app" : payload;

        if ("store-review".equals(target)) {
            // In-app review on Android needs Play Core (an SDK) -> belongs to the binding layer,
            // not this pure-system clean layer. Report it honestly instead of pretending.
            return RelayResult.of(0, "store-review needs Play Core (binding layer)");
        }

        Intent intent;
        if ("notification".equals(target) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", ctx.getPackageName(), null));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            ctx.startActivity(intent);
            return RelayResult.ok(null);
        } catch (Exception e) {
            return RelayResult.of(0, e.getMessage());
        }
    }
}
