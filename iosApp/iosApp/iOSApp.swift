import SwiftUI
import UserNotifications
import Shared

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        _ = KoinInitKt.doInitKoin { _ in }
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            if granted {
                DispatchQueue.main.async {
                    application.registerForRemoteNotifications()
                }
            }
        }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        IosPushNotificationHandlerKt.onIosPushTokenReceived(deviceToken: deviceToken)
    }

    // Foreground notifications: banner, sound, badge
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .sound, .badge])
        } else {
            completionHandler([.alert, .sound, .badge])
        }
    }

    // Notification tap handler with deep linking
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        defer { completionHandler() }
        let userInfo = response.notification.request.content.userInfo
        handlePushDeepLink(userInfo: userInfo)
    }

    private func handlePushDeepLink(userInfo: [AnyHashable: Any]) {
        // Direct link or URL parameter
        if let linkString = userInfo["deep_link"] as? String ?? userInfo["url"] as? String ?? userInfo["link"] as? String {
            FlowDeepLinkHandlerKt.handleOpenUrl(urlString: linkString)
            return
        }

        // Chat by conversation_id
        if let conversationId = userInfo["conversation_id"] as? String ?? (userInfo["conversation_id"] as? NSNumber)?.stringValue {
            FlowDeepLinkHandlerKt.handleOpenUrl(urlString: "floow://chat/\(conversationId)")
            return
        }

        // Post by post_id
        if let postId = userInfo["post_id"] as? String ?? (userInfo["post_id"] as? NSNumber)?.stringValue {
            FlowDeepLinkHandlerKt.handleOpenUrl(urlString: "floow://post/\(postId)")
            return
        }

        // Profile by user_id
        if let userId = userInfo["user_id"] as? String ?? (userInfo["user_id"] as? NSNumber)?.stringValue {
            FlowDeepLinkHandlerKt.handleOpenUrl(urlString: "floow://profile/\(userId)")
            return
        }

        // Replies / notifications
        if let type = userInfo["type"] as? String, type == "reply" || type == "replies" {
            FlowDeepLinkHandlerKt.handleOpenUrl(urlString: "floow://replies")
            return
        }
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    let urlString = url.absoluteString
                    if urlString.contains("code=") && (urlString.contains("googleusercontent.apps") || urlString.contains("oauth")) {
                        if IosGoogleOAuth.companion.handleCallbackUrl(url: urlString) {
                            return
                        }
                    }
                    FlowDeepLinkHandlerKt.handleOpenUrl(urlString: urlString)
                }
        }
    }
}
