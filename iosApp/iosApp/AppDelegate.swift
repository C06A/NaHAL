import UIKit
import NahalUI

// Minimal UIKit host. Everything visible comes from Kotlin: MainViewController() is the
// ComposeUIViewController declared in ui/src/iosMain/.../IOSEntry.kt, which hosts NaHalNavigator().
// Kotlin's top-level functions in IOSEntry.kt are exposed to Swift as IOSEntryKt.

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = IOSEntryKt.MainViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
