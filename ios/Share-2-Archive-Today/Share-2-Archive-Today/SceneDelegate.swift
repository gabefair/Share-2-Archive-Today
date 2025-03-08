//
//  SceneDelegate.swift
//  Share-2-Archive-Today
//
//  Created by Gabirel Fair on 2/9/25.
//
import UIKit
import SafariServices
import os.log

/// Manages the app's window scene and handles URL-based interactions
/// This delegate is responsible for managing the app's window scene lifecycle and URL-based interactions,
/// including handling URLs opened from the share extension
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    /// The window instance for the app when not using scenes
    var window: UIWindow?
    
    /// Logger for debugging
    private let logger = Logger(subsystem: "org.Gnosco.Share-2-Archive-Today", category: "SceneDelegate")
        
    // MARK: - URL Handling Methods
        
    /// Handles URLs when the app is opened via a URL scheme
    /// - Parameters:
    ///   - scene: The UIScene instance that received the URL
    ///   - URLContexts: A set of URL contexts containing the URLs to handle
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard let urlContext = URLContexts.first else {
            logger.error("No URL contexts provided")
            return
        }
        
        let incomingURL = urlContext.url
        logger.info("Received URL in scene: \(incomingURL)")
        
        if incomingURL.scheme == "share2archivetoday" {
            if let components = URLComponents(url: incomingURL, resolvingAgainstBaseURL: true),
               let queryItem = components.queryItems?.first(where: { $0.name == "url" }),
               let receivedUrlString = queryItem.value,
               let decodedURL = receivedUrlString.removingPercentEncoding {
                
                // Process the URL directly from the URL parameters
                processArchivedURL(decodedURL)
            } else {
                // No URL in parameters, check if we need to process a pending URL
                checkForPendingURL()
            }
        } else {
            logger.error("Unsupported URL scheme: \(incomingURL.scheme ?? "nil")")
        }
    }
    
    /// Processes a URL that needs to be archived
    /// - Parameter urlString: The URL string to process
    private func processArchivedURL(_ urlString: String) {
        logger.info("Processing URL for archiving: \(urlString)")
        
        // Process the URL to clean tracking parameters
        let processedURL = URLProcessor.processURL(urlString)
        logger.info("URL processed: \(urlString) -> \(processedURL)")
        
        // Create and open archive.today URL with processed URL
        if let archiveURL = createArchiveURL(from: processedURL) {
            presentArchivePage(with: archiveURL)
        } else {
            logger.error("Failed to create archive URL from: \(processedURL)")
        }
    }
    
    /// Checks if there's a pending URL from the share extension that needs to be processed
    private func checkForPendingURL() {
        logger.info("Checking for pending URL from share extension")
        
        if let defaults = UserDefaults(suiteName: "group.org.Gnosco.Share-2-Archive-Today") {
            let hasPendingURL = defaults.bool(forKey: "pendingArchiveURL")
            
            if hasPendingURL, let urlString = defaults.string(forKey: "lastSharedURL") {
                logger.info("Found pending URL: \(urlString)")
                
                // Clear the pending flag
                defaults.set(false, forKey: "pendingArchiveURL")
                
                // Process the URL
                processArchivedURL(urlString)
            } else {
                logger.info("No pending URL found")
            }
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
        guard let windowScene = window?.windowScene,
              let rootViewController = windowScene.windows.first?.rootViewController else {
            logger.error("Could not get root view controller")
            return
        }
        
        let safariVC = SFSafariViewController(url: url)
        safariVC.preferredControlTintColor = .systemBlue
        
        logger.info("Presenting Safari View Controller with URL: \(url)")
        
        if let presented = rootViewController.presentedViewController {
            logger.debug("Dismissing existing presented view controller")
            presented.dismiss(animated: true) {
                rootViewController.present(safariVC, animated: true)
            }
        } else {
            rootViewController.present(safariVC, animated: true)
        }
    }
    
    /// Called when the scene becomes active to check for pending URLs
    /// - Parameter scene: The scene that became active
    func sceneDidBecomeActive(_ scene: UIScene) {
        logger.debug("Scene became active")
        
        // Check for pending URLs from share extension
        checkForPendingURL()
    }
        
    /// Handles continuation of user activity, typically from Universal Links or Handoff
    /// - Parameters:
    ///   - scene: The UIScene instance that will continue the activity
    ///   - userActivity: The user activity to continue
    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        if let url = userActivity.webpageURL {
            logger.info("Continuing user activity with URL: \(url)")
            processURL(from: url)
        }
    }
        
    /// Processes a URL and opens it in archive.today
    /// - Parameter url: The URL to process
    private func processURL(from url: URL) {
        var finalUrlString: String? = nil
        
        if let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
           let urlQueryItem = components.queryItems?.first(where: { $0.name == "url" }) {
            finalUrlString = urlQueryItem.value
            logger.debug("Extracted URL from query parameters: \(urlQueryItem.value ?? "nil")")
        } else {
            // Direct URL case
            finalUrlString = url.absoluteString
            logger.debug("Using direct URL: \(url.absoluteString)")
        }

        guard let urlString = finalUrlString else {
            logger.error("Could not extract URL string")
            return
        }

        // Process the URL to clean tracking parameters
        let processedURL = URLProcessor.processURL(urlString)
        
        // Save the processed URL to URLStore before archiving
        URLStore.shared.saveURL(processedURL)
        logger.info("Processed URL saved to store: \(processedURL)")
        
        // Process the URL for archiving
        processArchivedURL(processedURL)
    }

    /// Called when the scene is being released by the system
    func sceneDidDisconnect(_ scene: UIScene) {
        logger.debug("Scene disconnected")
    }

    /// Called when the scene is about to move from an active state to an inactive state
    func sceneWillResignActive(_ scene: UIScene) {
        logger.debug("Scene will resign active")
    }

    /// Called as the scene transitions from the background to the foreground
    func sceneWillEnterForeground(_ scene: UIScene) {
        logger.debug("Scene will enter foreground")
    }

    /// Called as the scene transitions from the foreground to the background
    func sceneDidEnterBackground(_ scene: UIScene) {
        logger.debug("Scene did enter background")
    }
}
