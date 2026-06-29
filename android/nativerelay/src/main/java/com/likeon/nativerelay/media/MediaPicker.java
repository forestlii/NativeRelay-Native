/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.media;

import android.content.Context;

import com.likeon.nativerelay.RelayResult;
import com.likeon.nativerelay.internal.ProxyFlow;
import com.likeon.nativerelay.ui.NativeRelayProxyActivity;

/**
 * Capability: pick an image/video from the library. payload = image / video. data = a file path in
 * the app cache (the picked content is copied there). Uses the transparent proxy Activity, so no
 * Unity Activity is required.
 */
public final class MediaPicker {

    private MediaPicker() {}

    private static final long TIMEOUT_SECONDS = 300L; // user-driven

    public static RelayResult pick(Context ctx, String mediaType) throws InterruptedException {
        String arg = "video".equals(mediaType) ? "video" : "image";
        return ProxyFlow.run(ctx, NativeRelayProxyActivity.ACTION_PICK, arg, TIMEOUT_SECONDS);
    }
}
