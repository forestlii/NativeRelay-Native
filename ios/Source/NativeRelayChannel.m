/*
 * Copyright (c) 2026 Likeon. Licensed under the MIT License.
 *
 * NativeRelay — iOS native channel reference implementation (Objective-C).
 * Implements the C ABI in NativeRelayChannel.h and dispatches commands (RelayCommand.h, generated
 * by tools/codegen) to built-in clean-layer capabilities.
 *
 * Batch A capabilities implemented here: GetDeviceInfo, GetNetworkStatus, Vibrate, OpenSettings.
 * Commands 1–6 (permission / location / media / album / camera / scan) arrive in later batches.
 *
 * ⚠️ NOT compiled / device-verified — iOS builds need macOS + Xcode (authored on Windows).
 *    Reference implementation. Frameworks to link in the iOS build: UIKit, SystemConfiguration,
 *    AudioToolbox, StoreKit (Foundation is implicit). Drop this .m + the two .h into
 *    Assets/Plugins/iOS/ and Unity compiles them into the generated Xcode app.
 */
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <SystemConfiguration/SystemConfiguration.h>
#import <AudioToolbox/AudioToolbox.h>
#import <StoreKit/StoreKit.h>
#import <netinet/in.h>
#import <string.h>
#import "NativeRelayChannel.h"
#import "RelayCommand.h"

/* All IosChannel instances register the SAME static C# trampoline; per-channel identity travels
 * in `context` (a GCHandle), echoed back on every callback. So one global pointer is correct. */
static NativeRelayResultCallback gCallback = NULL;

#pragma mark - Capabilities (clean layer; iOS has no Activity)

/* GetDeviceInfo -> json string. */
static NSString* NativeRelay_deviceInfo(void) {
    UIDevice* dev = UIDevice.currentDevice;
    UIScreen* scr = UIScreen.mainScreen;
    CGFloat scale = scr.scale;
    NSDictionary* info = @{
        @"model": dev.model ?: @"",
        @"manufacturer": @"Apple",
        @"os": @"ios",
        @"osVersion": dev.systemVersion ?: @"",
        @"lang": NSLocale.preferredLanguages.firstObject ?: @"",
        @"totalMemMB": @(NSProcessInfo.processInfo.physicalMemory / (1024ULL * 1024ULL)),
        @"screenW": @((NSInteger)(scr.bounds.size.width * scale)),
        @"screenH": @((NSInteger)(scr.bounds.size.height * scale)),
        @"dpi": @((NSInteger)(scale * 160)),
    };
    NSData* json = [NSJSONSerialization dataWithJSONObject:info options:0 error:nil];
    return json ? [[NSString alloc] initWithData:json encoding:NSUTF8StringEncoding] : @"{}";
}

/* GetNetworkStatus -> wifi / cellular / none. Synchronous reachability check (we're already off
 * the main thread). iOS rarely has ethernet, so it collapses into the reachable/non-WWAN case. */
static NSString* NativeRelay_networkStatus(void) {
    struct sockaddr_in zero;
    memset(&zero, 0, sizeof(zero));
    zero.sin_len = sizeof(zero);
    zero.sin_family = AF_INET;

    SCNetworkReachabilityRef ref =
        SCNetworkReachabilityCreateWithAddress(NULL, (const struct sockaddr*)&zero);
    if (ref == NULL) return @"none";

    SCNetworkReachabilityFlags flags;
    BOOL ok = SCNetworkReachabilityGetFlags(ref, &flags);
    CFRelease(ref);
    if (!ok) return @"none";

    BOOL reachable = (flags & kSCNetworkReachabilityFlagsReachable) != 0;
    BOOL needsConnection = (flags & kSCNetworkReachabilityFlagsConnectionRequired) != 0;
    if (!reachable || needsConnection) return @"none";
    if ((flags & kSCNetworkReachabilityFlagsIsWWAN) != 0) return @"cellular";
    return @"wifi";
}

/* Vibrate. iOS haptics are not duration-based, so "ms:<n>" maps to medium; light/medium/heavy use
 * the impact generator. UIKit must run on the main thread. */
static NSString* NativeRelay_vibrate(NSString* payload, int* outCode) {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (@available(iOS 10.0, *)) {
            UIImpactFeedbackStyle style = UIImpactFeedbackStyleMedium;
            if ([payload isEqualToString:@"light"]) style = UIImpactFeedbackStyleLight;
            else if ([payload isEqualToString:@"heavy"]) style = UIImpactFeedbackStyleHeavy;
            UIImpactFeedbackGenerator* gen = [[UIImpactFeedbackGenerator alloc] initWithStyle:style];
            [gen prepare];
            [gen impactOccurred];
        } else {
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate);
        }
    });
    *outCode = 1;
    return nil;
}

/* OpenSettings. iOS exposes only the app's own settings page (no separate notification URL).
 * store-review uses StoreKit. UIKit/StoreKit must run on the main thread. */
static NSString* NativeRelay_openSettings(NSString* payload, int* outCode) {
    NSString* target = payload.length ? payload : @"app";

    if ([target isEqualToString:@"store-review"]) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (@available(iOS 10.3, *)) {
                [SKStoreReviewController requestReview];
            }
        });
        *outCode = 1;
        return nil;
    }

    NSURL* url = [NSURL URLWithString:UIApplicationOpenSettingsURLString];
    dispatch_async(dispatch_get_main_queue(), ^{
        UIApplication* app = UIApplication.sharedApplication;
        if ([app canOpenURL:url]) {
            [app openURL:url options:@{} completionHandler:nil];
        }
    });
    *outCode = 1;   // dispatched asynchronously; reported optimistically (reference impl)
    return nil;
}

/* Dispatch a command to a built-in capability. Returns the result text/json (or nil) and sets
 * *outCode. Unknown commands echo so the template stays usable for custom commands. */
static NSString* NativeRelay_handle(int command, NSString* payload, int* outCode) {
    *outCode = 1;
    switch (command) {
        case RelayCommandGetDeviceInfo:    return NativeRelay_deviceInfo();
        case RelayCommandGetNetworkStatus: return NativeRelay_networkStatus();
        case RelayCommandVibrate:          return NativeRelay_vibrate(payload, outCode);
        case RelayCommandOpenSettings:     return NativeRelay_openSettings(payload, outCode);
        default:
            return payload ?: @"";  // commands 1–6 arrive in later batches
    }
}

#pragma mark - C ABI

void NativeRelayChannel_Init(void* context, NativeRelayResultCallback cb) {
    gCallback = cb;  // context is per-channel; passed back on every callback
}

void NativeRelayChannel_Send(void* context, long long seed, int command, const char* payload) {
    NSString* input = payload ? [NSString stringWithUTF8String:payload] : @"";
    // Do the work OFF the main thread, then call back with the SAME seed.
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        int code = 1;
        NSString* data = nil;
        @try {
            data = NativeRelay_handle(command, input, &code);
        } @catch (NSException* e) {
            code = 0;
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
    // Release per-context native resources here. The built-in capabilities hold none.
    // Don't clear gCallback: other channels (other contexts) may still be using it.
}
