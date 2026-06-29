/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 *
 * NativeRelay — Android native channel reference implementation.
 * This is the Java side that the C# `AndroidChannel` (shipped in the NativeRelay UPM
 * package) talks to over JNI. It is a GENERIC relay template: it handles the
 * seed / thread / callback plumbing, and you fill in `handle()` per `command`.
 *
 * Contract (must match Runtime/Channels/AndroidChannel.cs):
 *   - class            com.likeon.nativerelay.NativeRelayChannel
 *   - constructor      NativeRelayChannel(ResultCallback callback)
 *   - method           void send(long seed, int command, String payload)
 *   - method           void dispose()
 *   - inner interface  ResultCallback { void onResult(long seed, int code, String data); }
 *
 * The C# side implements ResultCallback through an AndroidJavaProxy, so the class name,
 * the inner interface name, and these method names are reflected at runtime — keep them
 * exactly as-is (see consumer-rules.pro for the R8/ProGuard keep rules).
 */
package com.likeon.nativerelay;

import android.content.Context;

import com.likeon.nativerelay.device.DeviceInfo;
import com.likeon.nativerelay.haptic.Haptics;
import com.likeon.nativerelay.internal.AppContext;
import com.likeon.nativerelay.location.LocationOnce;
import com.likeon.nativerelay.media.AlbumSaver;
import com.likeon.nativerelay.net.NetworkStatus;
import com.likeon.nativerelay.system.SettingsOpener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generic relay channel: receive a request on the calling (Unity) thread, do the real work
 * OFF that thread, then call back with the SAME seed and a {@code (code, data)} pair.
 * <p>
 * Fill in {@link #handle(int, String)} per command. The framework never interprets the code
 * and never touches the data — it just relays them back to your C# business layer, where the
 * bridge dispatches them on Unity's main thread.
 */
public class NativeRelayChannel {

    /**
     * Result sink. The C# side provides this via an {@code AndroidJavaProxy}; it may be
     * invoked from any worker thread, which is exactly what NativeRelay expects.
     */
    public interface ResultCallback {
        /**
         * @param seed the request id to correlate with (echo the one you received in send)
         * @param code 1 = ok, 0 = fail, or any business code you define (e.g. 10086)
         * @param data result text or a file PATH (never raw bytes — see note below)
         */
        void onResult(long seed, int code, String data);
    }

    private final ResultCallback callback;
    private final ExecutorService worker = Executors.newCachedThreadPool();
    private volatile boolean disposed = false;

    public NativeRelayChannel(ResultCallback callback) {
        this.callback = callback;
    }

    /**
     * Called from C# ({@code AndroidChannel.Send}). Dispatches the work to a worker thread so
     * the caller is never blocked, then fires the result back carrying the same seed.
     */
    public void send(final long seed, final int command, final String payload) {
        if (disposed) {
            return;
        }
        worker.execute(new Runnable() {
            @Override
            public void run() {
                if (disposed) {
                    return;
                }
                try {
                    RelayResult result = handle(command, payload);
                    fire(seed, result.code, result.data);
                } catch (Throwable t) {
                    fire(seed, 0, t.getMessage());          // failure (your own error code)
                }
            }
        });
    }

    /**
     * Dispatch a command to a built-in capability, returning {@code (code, data)}. Command codes
     * come from the generated {@link RelayCommand} (tools/codegen). Commands 1–6 (permission /
     * location / media / album / camera / scan) arrive in later batches; an unknown command echoes
     * so the template stays usable for your own custom commands. For big binary (audio/image),
     * return a file PATH as data, not raw bytes.
     */
    private RelayResult handle(int command, String payload) throws Exception {
        switch (command) {
            case RelayCommand.GET_DEVICE_INFO:    return RelayResult.ok(DeviceInfo.get(appContext()));
            case RelayCommand.GET_NETWORK_STATUS: return RelayResult.ok(NetworkStatus.get(appContext()));
            case RelayCommand.VIBRATE:            return Haptics.vibrate(appContext(), payload);
            case RelayCommand.OPEN_SETTINGS:      return SettingsOpener.open(appContext(), payload);
            case RelayCommand.GET_LOCATION_ONCE:  return LocationOnce.get(appContext());
            case RelayCommand.SAVE_TO_ALBUM:      return AlbumSaver.save(appContext(), payload);
            default:
                return RelayResult.ok(payload != null ? payload : "");
        }
    }

    private static Context appContext() {
        Context c = AppContext.get();
        if (c == null) {
            throw new IllegalStateException("no application Context; inject via AppContext.set(...)");
        }
        return c;
    }

    private void fire(long seed, int code, String data) {
        final ResultCallback cb = callback;
        if (cb != null && !disposed) {
            cb.onResult(seed, code, data);
        }
    }

    /** Closing the channel: stop accepting work and tear down the worker pool. */
    public void dispose() {
        disposed = true;
        worker.shutdownNow();
    }
}
