/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 */
package com.likeon.nativerelay;

/** A capability's outcome: an int code (1=ok, 0=fail, or your own) + optional string data
 *  (text / file path / json). Lets each capability return its own code, not just success. */
public final class RelayResult {
    public final int code;
    public final String data;

    private RelayResult(int code, String data) {
        this.code = code;
        this.data = data;
    }

    public static RelayResult ok(String data) { return new RelayResult(1, data); }
    public static RelayResult fail(String message) { return new RelayResult(0, message); }
    public static RelayResult of(int code, String data) { return new RelayResult(code, data); }
}
