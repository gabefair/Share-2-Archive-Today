//
//  AppDelegate.swift
//  Share-2-Archive-Today
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
    
    /// Archive URL service
    private let archiveService = ArchiveURLService.shared
    
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
        
        // Get the root view controller
        guard let rootViewController = window?.rootViewController else {
            logger.error("Could not get root view controller")
            return false
        }
        
        // Don't try to create a UIOpenURLContext, instead modify the ArchiveURLService
        // to have a method that directly handles URLs
        if url.scheme == "share2archivetoday" {
            if let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
               let queryItem = components.queryItems?.first(where: { $0.name == "url" }),
               let receivedUrlString = queryItem.value,
               let decodedURL = receivedUrlString.removingPercentEncoding {
                
                // Process the URL directly
                archiveService.processAndArchiveURL(decodedURL, from: rootViewController)
            } else {
                // No URL in parameters, check for pending URL
                archiveService.checkForPendingURL(from: rootViewController)
            }
        } else {
            logger.error("Unsupported URL scheme: \(url.scheme ?? "nil")")
        }
        
        return true
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
        
        // Initialize Archive URL Service
        _ = ArchiveURLService.shared
        logger.debug("ArchiveURLService initialized")
        
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
