#import <Foundation/Foundation.h>
#import <LocalAuthentication/LocalAuthentication.h>

typedef NS_ENUM(NSInteger, AuthenticationResult) {
    AuthenticationSuccess = 0,
    AuthenticationFailed = 1,
    HardwareUnavailable = 2,
    AuthenticationNotSet = 3,
    FeatureUnavailable = 4
};

AuthenticationResult requestAuth(const char *reason) {
    NSString *nsReason = [NSString stringWithUTF8String:reason];
    __block AuthenticationResult result = AuthenticationFailed;
    __block BOOL done = NO;

    dispatch_async(dispatch_get_main_queue(), ^{
        LAContext *context = [[LAContext alloc] init];
        NSError *error = nil;

        if (![context canEvaluatePolicy:LAPolicyDeviceOwnerAuthentication error:&error]) {
            switch (error.code) {
                case LAErrorBiometryNotEnrolled:
                case LAErrorPasscodeNotSet:
                    result = AuthenticationNotSet;
                    break;
                case LAErrorBiometryNotAvailable:
                    result = FeatureUnavailable;
                    break;
                default:
                    result = AuthenticationFailed;
                    break;
            }
            done = YES;
            return;
        }

        [context evaluatePolicy:LAPolicyDeviceOwnerAuthentication
                 localizedReason:nsReason
                           reply:^(BOOL success, NSError * _Nullable authError) {
            result = success ? AuthenticationSuccess : AuthenticationFailed;
            done = YES;
        }];
    });

    NSDate *deadline = [NSDate dateWithTimeIntervalSinceNow:30.0];
    while (!done && [deadline timeIntervalSinceNow] > 0) {
        [[NSRunLoop mainRunLoop] runMode:NSDefaultRunLoopMode
                             beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.1]];
    }

    return done ? result : AuthenticationFailed;
}
