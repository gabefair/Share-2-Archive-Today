// 'Share 2 Archive Today/AppDelegate.swift'
import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Create window with the correct frame
        window = UIWindow(frame: UIScreen.main.bounds)
        
        // Create the root view controller
        let savedURLsViewController = SavedURLsViewController()
        
        // Create navigation controller with proper styling
        let navigationController = UINavigationController(rootViewController: savedURLsViewController)
        navigationController.navigationBar.prefersLargeTitles = true
        
        // Configure navigation bar appearance
        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = .systemBackground
        appearance.titleTextAttributes = [.foregroundColor: UIColor.label]
        appearance.largeTitleTextAttributes = [.foregroundColor: UIColor.label]
        
        navigationController.navigationBar.standardAppearance = appearance
        navigationController.navigationBar.scrollEdgeAppearance = appearance
        navigationController.navigationBar.compactAppearance = appearance
        
        // Set the root view controller
        window?.rootViewController = navigationController
        window?.backgroundColor = .systemBackground
        window?.makeKeyAndVisible()
        
        // Check for pending URLs
        checkPendingURL()
        
        return true
    }
    
    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey : Any] = [:]) -> Bool {
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
