/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.media;

import android.content.Context;

import com.likeon.nativerelay.RelayResult;
import com.likeon.nativerelay.internal.ProxyFlow;
import com.likeon.nativerelay.ui.NativeRelayProxyActivity;

/**
 * Capability: capture a photo with the system camera. data = a file path in the app cache. The
 * camera writes through the library's own content provider (no androidx FileProvider dependency),
 * via the transparent proxy Activity. ACTION_IMAGE_CAPTURE needs no CAMERA permission for the
 * calling app (the camera app owns that).
 */
public final class PhotoCapture {

    private PhotoCapture() {}

    private static final long TIMEOUT_SECONDS = 300L; // user-driven

    public static RelayResult capture(Context ctx) throws InterruptedException {
        return ProxyFlow.run(ctx, NativeRelayProxyActivity.ACTION_CAPTURE, null, TIMEOUT_SECONDS);
    }
}
