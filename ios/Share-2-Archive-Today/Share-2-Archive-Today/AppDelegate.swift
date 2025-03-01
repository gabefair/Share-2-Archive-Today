//
//  AppDelegate.swift
//  Share-2-Archive-Today
//
//  Created by Gabirel Fair on 2/9/25.
//
import UIKit
import SafariServices
import os.log

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    
    var window: UIWindow?
    
    /// The URL that was used to launch the app, if any
    private var launchURL: URL?
    
    /// Logger for debugging
    private let logger = Logger(subsystem: "org.Gnosco.Share-2-Archive-Today", category: "AppDelegate")
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Check if the app was launched from a URL
        if let url = launchOptions?[.url] as? URL {
            logger.info("App launched with URL: \(url)")
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
        logger.info("App opened with URL: \(url)")
        handleIncomingURL(url)
        return true
    }
    
    /// Processes URLs that are used to launch the app
    /// - Parameter url: The URL to process
    private func handleIncomingURL(_ url: URL) {
        // Verify the URL scheme matches our app
        guard url.scheme == "share2archivetoday" else {
            logger.error("URL scheme doesn't match: \(url.scheme ?? "nil")")
            return
        }
        
        logger.info("Processing incoming URL: \(url)")
        
        // Extract the shared URL from the incoming URL's query parameters
        if let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
           let urlQueryItem = components.queryItems?.first(where: { $0.name == "url" }),
           let urlString = urlQueryItem.value,
           let decodedURL = urlString.removingPercentEncoding {
            
            // Save the URL to the URLStore first
            URLStore.shared.saveURL(decodedURL)
            logger.info("URL saved to store: \(decodedURL)")
            
            if let archiveURL = createArchiveURL(from: decodedURL) {
                // Present the archive.today page in Safari View Controller
                logger.info("Opening archive.today URL: \(archiveURL)")
                presentArchivePage(with: archiveURL)
            } else {
                logger.error("Failed to create archive URL from: \(decodedURL)")
            }
        } else {
            logger.error("Could not extract URL from components: \(url)")
        }
    }
    
    /// Creates an archive.today URL from a given URL string
    /// - Parameter urlString: The URL to archive
    /// - Returns: A URL for the archive.today service, if creation was successful
    private func createArchiveURL(from urlString: String) -> URL? {
        guard let encodedUrl = urlString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            logger.error("Failed to encode URL: \(urlString)")
            return nil
        }
        
        let archiveUrlString = "https://archive.today/?run=1&url=\(encodedUrl)"
        logger.debug("Created archive URL: \(archiveUrlString)")
        return URL(string: archiveUrlString)
    }
    
    /// Presents the archive.today page in a Safari View Controller
    /// - Parameter url: The archive.today URL to present
    private func presentArchivePage(with url: URL) {
        // Get the root view controller
        guard let rootViewController = window?.rootViewController else {
            logger.error("Could not get root view controller")
            return
        }
        
        // Create and configure the Safari View Controller
        let safariVC = SFSafariViewController(url: url)
        safariVC.preferredControlTintColor = .systemBlue
        
        logger.info("Presenting Safari View Controller")
        
        // If a view controller is already being presented, dismiss it first
        if let presented = rootViewController.presentedViewController {
            logger.debug("Dismissing existing presented view controller")
            presented.dismiss(animated: true) {
                rootViewController.present(safariVC, animated: true)
            }
        } else {
            rootViewController.present(safariVC, animated: true)
        }
    }
    
    // MARK: - App Configuration
    
    private func setupAppConfiguration() {
        logger.info("Setting up app configuration")
        
        // Setup any required services
        setupServices()
    }
    
    private func setupServices() {
        // Initialize URLStore
        _ = URLStore.shared
        logger.debug("URLStore initialized")
        
        // Setup any other required services
    }
    
    // MARK: - UISceneSession Lifecycle
    
    func application(_ application: UIApplication,
                    configurationForConnecting connectingSceneSession: UISceneSession,
                    options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        logger.debug("Configuring scene session: \(String(describing: connectingSceneSession.role))")
        return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }
    
    func application(_ application: UIApplication,
                    didDiscardSceneSessions sceneSessions: Set<UISceneSession>) {
        // Handle any cleanup when scenes are discarded
        logger.debug("Discarded scene sessions: \(sceneSessions.count)")
    }
}
