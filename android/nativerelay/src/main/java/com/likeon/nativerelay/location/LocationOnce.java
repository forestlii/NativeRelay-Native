/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.location;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import com.likeon.nativerelay.RelayResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Clean-layer capability: one-shot location as json {lat,lng,acc}.
 *
 * <p>Uses the system {@link LocationManager} — NOT {@code FusedLocationProviderClient}, which would
 * pull in Google Play Services (a third-party dependency). Runs on the channel's worker thread and
 * blocks (with a timeout) for one fix, so the request callbacks are posted to the main looper.
 *
 * <p>Requires a location permission at runtime (ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION);
 * returns code 0 if it isn't granted. The permission is NOT declared in this library's manifest
 * (it's sensitive and optional) — add it to your app's manifest if you use this capability.
 */
public final class LocationOnce {

    private LocationOnce() {}

    private static final long TIMEOUT_SECONDS = 10L;

    public static RelayResult get(Context ctx) throws InterruptedException, JSONException {
        boolean fine = granted(ctx, "android.permission.ACCESS_FINE_LOCATION");
        boolean coarse = granted(ctx, "android.permission.ACCESS_COARSE_LOCATION");
        if (!fine && !coarse) {
            return RelayResult.of(0, "location permission not granted");
        }

        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return RelayResult.of(0, "no location service");
        }

        String provider = pickProvider(lm, fine);
        if (provider == null) {
            Location last = lastKnown(lm, fine);
            return last != null ? RelayResult.ok(toJson(last))
                                : RelayResult.of(0, "no location provider enabled");
        }

        final Location[] holder = new Location[1];
        final CountDownLatch latch = new CountDownLatch(1);
        final LocationListener listener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                holder[0] = location;
                latch.countDown();
            }
            @Override public void onStatusChanged(String p, int status, Bundle extras) { }
            @Override public void onProviderEnabled(String p) { }
            @Override public void onProviderDisabled(String p) { }
        };

        try {
            // Callbacks are posted to the main looper, so this can be called from a worker thread.
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper());
            boolean got = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (got && holder[0] != null) {
                return RelayResult.ok(toJson(holder[0]));
            }
            Location last = lastKnown(lm, fine);
            return last != null ? RelayResult.ok(toJson(last))
                                : RelayResult.of(0, "location timeout");
        } catch (SecurityException e) {
            return RelayResult.of(0, "location permission revoked");
        } finally {
            lm.removeUpdates(listener);
        }
    }

    private static boolean granted(Context ctx, String permission) {
        return ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static String pickProvider(LocationManager lm, boolean fine) {
        if (fine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        return null;
    }

    private static Location lastKnown(LocationManager lm, boolean fine) {
        try {
            Location l = fine ? lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) : null;
            if (l == null) {
                l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            return l;
        } catch (SecurityException e) {
            return null;
        }
    }

    private static String toJson(Location loc) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("lat", loc.getLatitude());
        o.put("lng", loc.getLongitude());
        o.put("acc", loc.hasAccuracy() ? loc.getAccuracy() : 0);
        return o.toString();
    }
}
