/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Clean-layer capability: current network transport — wifi / cellular / ethernet / none. */
public final class NetworkStatus {

    private NetworkStatus() {}

    public static String get(Context ctx) {
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return "none";

        Network network = cm.getActiveNetwork();
        if (network == null) return "none";
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return "none";

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "cellular";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ethernet";
        return "none";
    }
}
