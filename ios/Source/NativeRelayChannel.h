/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 *
 * NativeRelay — iOS native channel, C ABI contract.
 * This header declares the three C functions that the C# `IosChannel` (shipped in the
 * NativeRelay UPM package) imports via P/Invoke `[DllImport("__Internal")]`. They are plain
 * C linkage so they statically link into the Unity-generated iOS app.
 *
 * Contract (must match Runtime/Channels/IosChannel.cs):
 *   typedef void (*NativeRelayResultCallback)(void* context, long long seed, int code, const char* data);
 *   void NativeRelayChannel_Init(void* context, NativeRelayResultCallback cb);
 *   void NativeRelayChannel_Send(void* context, long long seed, int command, const char* payload);
 *   void NativeRelayChannel_Dispose(void* context);
 *
 * `context` is an opaque pointer C# hands in at Init (a GCHandle identifying the channel);
 * echo it back unchanged on every callback so C# can find the right channel.
 */
#ifndef NATIVE_RELAY_CHANNEL_H
#define NATIVE_RELAY_CHANNEL_H

#ifdef __cplusplus
extern "C" {
#endif

/* Called from native (on ANY thread) to hand a result back to C#.
 * data = UTF-8 C string (or NULL); it is valid only during this call — C# copies it at once. */
typedef void (*NativeRelayResultCallback)(void* context, long long seed, int code, const char* data);

/* Register the callback + per-channel context once. */
void NativeRelayChannel_Init(void* context, NativeRelayResultCallback cb);

/* Start a request. Do the work OFF the main thread, then call cb(context, seed, code, data)
 * carrying the SAME seed you received here. */
void NativeRelayChannel_Send(void* context, long long seed, int command, const char* payload);

/* Release any per-context native resources. */
void NativeRelayChannel_Dispose(void* context);

#ifdef __cplusplus
}
#endif

#endif /* NATIVE_RELAY_CHANNEL_H */
