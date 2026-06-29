/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import com.likeon.nativerelay.internal.NativeRelayFileProvider;
import com.likeon.nativerelay.internal.PendingRequests;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Transparent proxy Activity that performs the Activity-bound work the channel can't do from a plain
 * Context: requesting a runtime permission, picking media, or capturing a photo. It receives the
 * result in its own lifecycle callbacks and hands it back via {@link PendingRequests}, then finishes.
 * This keeps those capabilities Unity-free — no {@code UnityPlayer.currentActivity} required.
 */
public final class NativeRelayProxyActivity extends Activity {

    public static final String ACTION_PERMISSION = "permission";
    public static final String ACTION_PICK = "pick";
    public static final String ACTION_CAPTURE = "capture";

    public static final String EXTRA_REQUEST_ID = "nr_request_id";
    public static final String EXTRA_ACTION = "nr_action";
    public static final String EXTRA_ARG = "nr_arg";

    private static final int RC = 0x4E52; // 'NR'
    private static final String STATE_CAPTURE_PATH = "nr_capture_path";

    private String requestId;
    private String capturePath;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) {
            requestId = saved.getString(EXTRA_REQUEST_ID);
            capturePath = saved.getString(STATE_CAPTURE_PATH);
            return; // the request was already launched before this recreation
        }
        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        String action = getIntent().getStringExtra(EXTRA_ACTION);
        String arg = getIntent().getStringExtra(EXTRA_ARG);
        try {
            if (ACTION_PERMISSION.equals(action)) {
                requestPermissions(new String[]{arg}, RC);
            } else if (ACTION_PICK.equals(action)) {
                startActivityForResult(buildPickIntent(arg), RC);
            } else if (ACTION_CAPTURE.equals(action)) {
                startCapture();
            } else {
                finishWith(0, "unknown action: " + action);
            }
        } catch (Exception e) {
            finishWith(0, e.getMessage());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(EXTRA_REQUEST_ID, requestId);
        out.putString(STATE_CAPTURE_PATH, capturePath);
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] permissions, int[] results) {
        if (rc != RC) {
            return;
        }
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        finishWith(granted ? 1 : 0, granted ? "granted" : "denied");
    }

    @Override
    protected void onActivityResult(int rc, int resultCode, Intent data) {
        if (rc != RC) {
            finishWith(0, "bad request code");
            return;
        }
        if (resultCode != Activity.RESULT_OK) {
            finishWith(0, "cancelled");
            return;
        }
        try {
            if (ACTION_CAPTURE.equals(getIntent().getStringExtra(EXTRA_ACTION))) {
                finishWith(capturePath != null ? 1 : 0, capturePath);
            } else {
                Uri uri = (data != null) ? data.getData() : null;
                if (uri == null) {
                    finishWith(0, "no media returned");
                    return;
                }
                String path = copyToCache(uri);
                finishWith(path != null ? 1 : 0, path != null ? path : "copy failed");
            }
        } catch (Exception e) {
            finishWith(0, e.getMessage());
        }
    }

    private Intent buildPickIntent(String mediaType) {
        String mime = "video".equals(mediaType) ? "video/*" : "image/*";
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mime);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        return intent;
    }

    private void startCapture() throws Exception {
        File dir = new File(getCacheDir(), NativeRelayFileProvider.SHARE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            finishWith(0, "could not create cache dir");
            return;
        }
        File out = new File(dir, "nr_capture_" + System.currentTimeMillis() + ".jpg");
        out.createNewFile();
        capturePath = out.getAbsolutePath();

        Uri output = NativeRelayFileProvider.uriFor(this, out);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, output);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, RC);
    }

    private String copyToCache(Uri uri) throws Exception {
        File dir = new File(getCacheDir(), NativeRelayFileProvider.SHARE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        File out = new File(dir, "nr_pick_" + System.currentTimeMillis() + guessExt(getContentResolver().getType(uri)));
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) {
                return null;
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        }
        return out.getAbsolutePath();
    }

    private static String guessExt(String mime) {
        if (mime == null) return "";
        if (mime.contains("png")) return ".png";
        if (mime.contains("webp")) return ".webp";
        if (mime.contains("gif")) return ".gif";
        if (mime.startsWith("video")) return ".mp4";
        return ".jpg";
    }

    private void finishWith(int code, String data) {
        if (requestId != null) {
            PendingRequests.complete(requestId, code, data);
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
