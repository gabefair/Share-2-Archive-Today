//
//  SceneDelegate.swift
//  Share-2-Archive-Today
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
    
    /// Archive URL service
    private let archiveService = ArchiveURLService.shared
    
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
        
        // Get the root view controller for the window scene
        guard let windowScene = window?.windowScene,
              let rootViewController = windowScene.windows.first?.rootViewController else {
            logger.error("Could not get root view controller")
            return
        }
        
        // Hand off to the shared service
        archiveService.handleURLContext(urlContext, from: rootViewController)
    }
    
    /// Called when the scene becomes active to check for pending URLs
    /// - Parameter scene: The scene that became active
    func sceneDidBecomeActive(_ scene: UIScene) {
        logger.debug("Scene became active")
        
        // Get the root view controller for the window scene
        guard let windowScene = window?.windowScene,
              let rootViewController = windowScene.windows.first?.rootViewController else {
            logger.error("Could not get root view controller")
            return
        }
        
        // Check for pending URLs
        archiveService.checkForPendingURL(from: rootViewController)
    }
        
    /// Handles continuation of user activity, typically from Universal Links or Handoff
    /// - Parameters:
    ///   - scene: The UIScene instance that will continue the activity
    ///   - userActivity: The user activity to continue
    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        if let url = userActivity.webpageURL {
            logger.info("Continuing user activity with URL: \(url)")
            
            // Get the root view controller for the window scene
            guard let windowScene = window?.windowScene,
                  let rootViewController = windowScene.windows.first?.rootViewController else {
                logger.error("Could not get root view controller")
                return
            }
            
            // Process the URL
            processURL(from: url, viewController: rootViewController)
        }
    }
        
    /// Processes a URL and opens it in archive.today
    /// - Parameters:
    ///   - url: The URL to process
    ///   - viewController: The view controller to present from
    private func processURL(from url: URL, viewController: UIViewController) {
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

        // Use the archive service to process and archive the URL
        archiveService.processAndArchiveURL(urlString, from: viewController)
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
