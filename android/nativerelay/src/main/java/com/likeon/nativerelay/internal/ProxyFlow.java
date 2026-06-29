/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.internal;

import android.content.Context;
import android.content.Intent;

import com.likeon.nativerelay.RelayResult;
import com.likeon.nativerelay.ui.NativeRelayProxyActivity;

import java.util.concurrent.TimeUnit;

/**
 * Shared "launch the transparent proxy Activity and block the worker thread for its result" flow,
 * used by the Activity-bound capabilities (permission / pick / capture). No Unity Activity needed —
 * the library's own proxy Activity does the {@code requestPermissions} / {@code startActivityForResult}.
 */
public final class ProxyFlow {

    private ProxyFlow() {}

    public static RelayResult run(Context ctx, String action, String arg, long timeoutSeconds)
            throws InterruptedException {
        PendingRequests.Pending pending = new PendingRequests.Pending();
        String id = PendingRequests.begin(pending);

        Intent intent = new Intent(ctx, NativeRelayProxyActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(NativeRelayProxyActivity.EXTRA_REQUEST_ID, id);
        intent.putExtra(NativeRelayProxyActivity.EXTRA_ACTION, action);
        if (arg != null) {
            intent.putExtra(NativeRelayProxyActivity.EXTRA_ARG, arg);
        }
        ctx.startActivity(intent);

        if (!pending.latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            PendingRequests.complete(id, 0, "timeout"); // remove + unblock if it lands late
            return RelayResult.of(0, "timeout");
        }
        return RelayResult.of(pending.code, pending.data);
    }
}
