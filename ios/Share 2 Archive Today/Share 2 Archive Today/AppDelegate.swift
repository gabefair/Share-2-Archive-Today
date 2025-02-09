// AppDelegate.swift
import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        window = UIWindow(frame: UIScreen.main.bounds)
        window?.rootViewController = UINavigationController(rootViewController: SavedURLsViewController())
        window?.makeKeyAndVisible()
        
        // Check for pending URLs
        checkPendingURL()
        
        return true
    }
    
    private func checkPendingURL() {
        let appGroupID = "group.org.Gnosco.Share-2-Archive-Today"
        if let userDefaults = UserDefaults(suiteName: appGroupID),
           let urlString = userDefaults.string(forKey: "pendingArchiveURL"),
           let url = URL(string: urlString) {
            // Clear the pending URL
            userDefaults.removeObject(forKey: "pendingArchiveURL")
            userDefaults.synchronize()
            
            // Open the URL
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        }
    }
}

extension AppDelegate {
    func applicationDidBecomeActive(_ application: UIApplication) {
        checkPendingURL()
    }
}

