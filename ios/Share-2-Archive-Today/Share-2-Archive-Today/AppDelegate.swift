//
//  AppDelegate.swift
//  Share-2-Archive-Today
//
//  Created by Gabirel Fair on 2/9/25.
//
import UIKit
import SafariServices

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    
    var window: UIWindow?
    
    /// The URL that was used to launch the app, if any
    private var launchURL: URL?
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Check if the app was launched from a URL
        if let url = launchOptions?[.url] as? URL {
            launchURL = url
        }
        
        // Setup any required app configurations
        setupAppConfiguration()
        
        return true
    }
    
    // MARK: - URL Handling
    
    func application(_ app: UIApplication,
                    open url: URL,
                    options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        // Handle URLs coming from the share extension
        handleIncomingURL(url)
        return true
    }
    
    /// Processes URLs that are used to launch the app
    /// - Parameter url: The URL to process
    private func handleIncomingURL(_ url: URL) {
        // Verify the URL scheme matches our app
        guard url.scheme == "share2archivetoday" else { return }
        
        // Extract the shared URL from the incoming URL's query parameters
        if let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
           let urlQueryItem = components.queryItems?.first(where: { $0.name == "url" }),
           let urlString = urlQueryItem.value,
           let decodedURL = urlString.removingPercentEncoding,
           let archiveURL = createArchiveURL(from: decodedURL) {
            
            // Present the archive.today page in Safari View Controller
            presentArchivePage(with: archiveURL)
        }
    }
    
    /// Creates an archive.today URL from a given URL string
    /// - Parameter urlString: The URL to archive
    /// - Returns: A URL for the archive.today service, if creation was successful
    private func createArchiveURL(from urlString: String) -> URL? {
        guard let encodedUrl = urlString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return nil
        }
        
        return URL(string: "https://archive.today/?run=1&url=\(encodedUrl)")
    }
    
    /// Presents the archive.today page in a Safari View Controller
    /// - Parameter url: The archive.today URL to present
    private func presentArchivePage(with url: URL) {
        // Get the root view controller
        guard let rootViewController = window?.rootViewController else { return }
        
        // Create and configure the Safari View Controller
        let safariVC = SFSafariViewController(url: url)
        safariVC.preferredControlTintColor = .systemBlue
        
        // If a view controller is already being presented, dismiss it first
        if let presented = rootViewController.presentedViewController {
            presented.dismiss(animated: true) {
                rootViewController.present(safariVC, animated: true)
            }
        } else {
            rootViewController.present(safariVC, animated: true)
        }
    }
    
    // MARK: - App Configuration
    
    private func setupAppConfiguration() {
        // Register for any necessary notifications
        registerForNotifications()
        
        // Setup any required services
        setupServices()
    }
    
    private func registerForNotifications() {
        // Request notification permissions if needed
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if granted {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
    }
    
    private func setupServices() {
        // Initialize URLStore
        _ = URLStore.shared
        
        // Setup any other required services
    }
    
    // MARK: - UISceneSession Lifecycle
    
    func application(_ application: UIApplication,
                    configurationForConnecting connectingSceneSession: UISceneSession,
                    options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }
    
    func application(_ application: UIApplication,
                    didDiscardSceneSessions sceneSessions: Set<UISceneSession>) {
        // Handle any cleanup when scenes are discarded
    }
}
