import UIKit
import Shared

/// Asks iOS for this install's APNs device token at every launch and hands it to the shared code,
/// which registers it with the relay; the relay pushes to it straight through Apple's push service
/// (see `onApnsToken` in IosApp.kt, and "APNs" in relay/relay.py). The banners themselves need the
/// notification permission the app already asks for; the token comes either way.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        application.registerForRemoteNotifications()
        if let shortcut = launchOptions?[.shortcutItem] as? UIApplicationShortcutItem {
            _ = IosAppKt.onOpenUrl(url: shortcut.type)
        }
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        if let shortcut = options.shortcutItem {
            _ = IosAppKt.onOpenUrl(url: shortcut.type)
        }
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = QuickActionSceneDelegate.self
        return configuration
    }

    func application(
        _ application: UIApplication,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(IosAppKt.onOpenUrl(url: shortcutItem.type))
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        IosAppKt.onApnsToken(token: deviceToken.map { String(format: "%02x", $0) }.joined())
    }

    /// A push that asks to wake the app. Only the relay's "rain on the way" and its skiing ones do
    /// (`content-available`, see "rain soon" and "ski notices" in relay/relay.py): iOS has drawn the
    /// banner already, and the shared code gives it the words and picture the app's own notifications
    /// wear. iOS decides whether this runs at all.
    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        if let payload = userInfo["ski_notice"] as? String, let key = userInfo["notif_id"] as? String {
            IosAppKt.onSkiPush(payload: payload, key: key) { completionHandler(.newData) }
            return
        }
        guard let payload = userInfo["weather_soon"] as? String, let key = userInfo["notif_id"] as? String else {
            completionHandler(.noData)
            return
        }
        IosAppKt.onWeatherPush(payload: payload, key: key) { completionHandler(.newData) }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // A simulator, or a build signed without the Push Notifications capability: the app keeps
        // its own in-app alerts, as before.
        print("HomeSafe: no APNs token: \(error.localizedDescription)")
    }
}

/// Receives Home Screen Quick Actions while the app is already running on a Scene-based SwiftUI app.
final class QuickActionSceneDelegate: NSObject, UIWindowSceneDelegate {
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(IosAppKt.onOpenUrl(url: shortcutItem.type))
    }
}
