/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.internal;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal zero-dependency {@link ContentProvider} that vends files from {@code cacheDir/nr_share},
 * so the camera can be handed a {@code content://} output Uri WITHOUT depending on androidx
 * {@code FileProvider}. Declared in the library manifest with authority
 * {@code ${applicationId}.nativerelayfiles}.
 */
public final class NativeRelayFileProvider extends ContentProvider {

    public static final String SHARE_DIR = "nr_share";

    public static Uri uriFor(Context ctx, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(ctx.getPackageName() + ".nativerelayfiles")
                .path(file.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File dir = new File(getContext().getCacheDir(), SHARE_DIR);
        File file = new File(dir, uri.getLastPathSegment());
        int flags = "r".equals(mode)
                ? ParcelFileDescriptor.MODE_READ_ONLY
                : ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE;
        return ParcelFileDescriptor.open(file, flags);
    }

    // Unused CRUD surface — the camera only needs openFile().
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String s, String[] a) { return 0; }
}
