/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 *
 * NativeRelay — iOS native channel reference implementation (Objective-C).
 * Implements the C ABI in NativeRelayChannel.h that the C# `IosChannel` talks to over
 * P/Invoke. Generic relay template: it handles the thread/callback plumbing; you fill in
 * `handle()` per `command`, returning (int code, NSString* data).
 *
 * Integration: drop this .m + NativeRelayChannel.h into your Unity project under
 * `Assets/Plugins/iOS/`. Unity's iOS build compiles them into the generated Xcode app
 * (statically linked, so `[DllImport("__Internal")]` resolves them).
 *
 * NOTE: iOS builds require macOS + Xcode. This file is a reference implementation aligned to
 * the contract; build and verify it on a device in your iOS environment.
 */
#import <Foundation/Foundation.h>
#import "NativeRelayChannel.h"

/* All IosChannel instances register the SAME static C# trampoline, so a single global pointer
 * is correct even with multiple channels: per-channel identity travels in `context` (a
 * GCHandle), which we echo back on every callback. */
static NativeRelayResultCallback gCallback = NULL;

/* Plug your real native work here, dispatched by command. Return the result as text, or — for
 * big binary like audio/image — save it to a file natively and return the PATH (load it
 * Unity-side). Do NOT push raw bytes through the result string. */
static NSString* NativeRelayChannel_handle(int command, NSString* payload) {
    switch (command) {
        // Example — wire your own commands here. The int values are defined by the caller
        // (a C# enum cast to int); this side just switches on them.
        //
        // case 1: return startRecording(payload);   // e.g. return a recorded file path
        // case 2: return recognize(payload);        // e.g. return recognized text
        default:
            return payload ?: @"";  // echo by default (handy for smoke tests)
    }
}

void NativeRelayChannel_Init(void* context, NativeRelayResultCallback cb) {
    gCallback = cb;  // context is per-channel; passed back on every callback
}

void NativeRelayChannel_Send(void* context, long long seed, int command, const char* payload) {
    NSString* input = payload ? [NSString stringWithUTF8String:payload] : @"";
    // Do the work OFF the main thread, then call back with the SAME seed.
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        int code = 1;          // success (your own success code)
        NSString* data;
        @try {
            data = NativeRelayChannel_handle(command, input);  // <-- your actual native work
        } @catch (NSException* e) {
            code = 0;          // failure (your own error code)
            data = e.reason ?: @"error";
        }
        NativeRelayResultCallback cb = gCallback;
        if (cb) {
            // const char* is valid only during this call — C# copies it immediately.
            cb(context, seed, code, data ? [data UTF8String] : NULL);
        }
    });
}

void NativeRelayChannel_Dispose(void* context) {
    // Release per-context native resources here. The echo sample holds none.
    // Don't clear gCallback: other channels (other contexts) may still be using it.
}
