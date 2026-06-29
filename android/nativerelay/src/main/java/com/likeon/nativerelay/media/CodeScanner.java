/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.media;

import android.content.Context;

import com.likeon.nativerelay.RelayResult;

/**
 * Capability: scan a QR / barcode — <b>documented stub</b>.
 *
 * <p>Unlike iOS (which has {@code AVCaptureMetadataOutput}), Android has <b>no system intent</b> for
 * barcode scanning. A real implementation needs a camera preview UI plus a decoder from a
 * third-party library (Google MLKit Barcode Scanning, or ZXing) — which would break
 * NativeRelay-Native's zero-dependency rule. So this is intentionally a stub: wire it up in your own
 * project (add MLKit/ZXing, decode in a proxy Activity, return the scanned string as data).
 */
public final class CodeScanner {

    private CodeScanner() {}

    public static RelayResult scan(Context ctx) {
        return RelayResult.of(0, "scan not implemented: add MLKit/ZXing in your project (see docs)");
    }
}
