/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bridges an async UI flow (a proxy Activity requesting a permission / picking media / capturing a
 * photo) back to the worker thread that started it. The capability registers a {@link Pending},
 * starts the proxy Activity, and blocks on the latch; the proxy Activity calls {@link #complete}
 * with the result.
 */
public final class PendingRequests {

    private PendingRequests() {}

    /** A single in-flight request: latch + its result once {@link #complete} fills it. */
    public static final class Pending {
        public final CountDownLatch latch = new CountDownLatch(1);
        public volatile int code;
        public volatile String data;
    }

    private static final ConcurrentHashMap<String, Pending> MAP = new ConcurrentHashMap<>();
    private static final AtomicLong SEQ = new AtomicLong();

    /** Register a pending request and get its id (put into the proxy Activity's Intent). */
    public static String begin(Pending pending) {
        String id = "nr-" + SEQ.incrementAndGet();
        MAP.put(id, pending);
        return id;
    }

    /** Deliver a result and release the waiting capability. No-op if the id is unknown/already done. */
    public static void complete(String id, int code, String data) {
        Pending p = (id != null) ? MAP.remove(id) : null;
        if (p != null) {
            p.code = code;
            p.data = data;
            p.latch.countDown();
        }
    }
}
