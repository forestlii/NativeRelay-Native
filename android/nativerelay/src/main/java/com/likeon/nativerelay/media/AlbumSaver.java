/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.media;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.likeon.nativerelay.RelayResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Clean-layer capability: save an image/video file to the photo album via {@link MediaStore}.
 *
 * <p>On API 29+ this uses scoped storage (RELATIVE_PATH + IS_PENDING) and needs NO permission.
 * On API 23–28 writing through MediaStore needs WRITE_EXTERNAL_STORAGE — add it to your app's
 * manifest (with {@code android:maxSdkVersion="28"}); a missing permission surfaces as code 0.
 * The permission is intentionally NOT declared in this library's manifest.
 *
 * <p>payload = absolute path of the source file. data = the saved content Uri.
 */
public final class AlbumSaver {

    private AlbumSaver() {}

    public static RelayResult save(Context ctx, String path) {
        if (path == null || path.isEmpty()) {
            return RelayResult.of(0, "empty path");
        }
        File src = new File(path);
        if (!src.exists() || !src.isFile()) {
            return RelayResult.of(0, "file not found: " + path);
        }

        boolean isVideo = isVideo(path);
        Uri collection = isVideo
                ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, src.getName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType(path, isVideo));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    isVideo ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }

        ContentResolver resolver = ctx.getContentResolver();
        Uri item = resolver.insert(collection, values);
        if (item == null) {
            return RelayResult.of(0, "MediaStore insert failed");
        }

        try (InputStream in = new FileInputStream(src);
             OutputStream out = resolver.openOutputStream(item)) {
            if (out == null) {
                resolver.delete(item, null, null);
                return RelayResult.of(0, "could not open output stream");
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            resolver.delete(item, null, null);
            return RelayResult.of(0, e.getMessage());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(item, done, null, null);
        }
        return RelayResult.ok(item.toString());
    }

    private static boolean isVideo(String path) {
        String p = path.toLowerCase();
        return p.endsWith(".mp4") || p.endsWith(".mov") || p.endsWith(".mkv")
                || p.endsWith(".webm") || p.endsWith(".3gp");
    }

    private static String mimeType(String path, boolean isVideo) {
        String p = path.toLowerCase();
        if (isVideo) {
            return "video/mp4";
        }
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
