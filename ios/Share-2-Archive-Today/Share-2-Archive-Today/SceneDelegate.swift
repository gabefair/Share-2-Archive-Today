//
//  SceneDelegate.swift
//  Share-2-Archive-Today
//
//  Created by Gabirel Fair on 2/9/25.
//
import UIKit
import SafariServices

/// Manages the app's window scene and handles URL-based interactions
/// This delegate is responsible for managing the app's window scene lifecycle and URL-based interactions,
/// including handling URLs opened from the share extension
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    /// The window instance for the app when not using scenes
    /// The window instance for the app when not using scenes
    var window: UIWindow?
        
    // MARK: - URL Handling Methods
        
        /// Handles URLs when the app is opened via a URL scheme
        /// - Parameters:
        ///   - scene: The UIScene instance that received the URL
        ///   - URLContexts: A set of URL contexts containing the URLs to handle
        func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
            guard let urlContext = URLContexts.first else { return }
            processURL(from: urlContext.url)
        }
        
        /// Handles continuation of user activity, typically from Universal Links or Handoff
        /// - Parameters:
        ///   - scene: The UIScene instance that will continue the activity
        ///   - userActivity: The user activity to continue
        func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
            if let url = userActivity.webpageURL {
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
        } else {
            // Direct URL case
            finalUrlString = url.absoluteString
        }

        guard let urlString = finalUrlString,
              let encodedUrl = urlString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let archiveUrl = URL(string: "https://archive.today/?run=1&url=\(encodedUrl)") else {
            return
        }

        UIApplication.shared.open(archiveUrl, options: [:], completionHandler: nil)
    }
    
    // MARK: - Helper Methods
    
    private func openArchivedURL(_ url: URL) {
        UIApplication.shared.open(url, options: [:], completionHandler: nil)
    }
        
        /// Called when scene sessions are discarded
        /// - Parameters:
        ///   - application: The singleton app instance
        ///   - sceneSessions: The set of scenes that were discarded
        func application(_ application: UIApplication, didDiscardSceneSessions sceneSessions: Set<UISceneSession>) {
            // Clean up any resources associated with discarded scenes
        }

    /// Called when the scene is being released by the system
    /// - Parameter scene: The scene that is being released
    /// This occurs shortly after the scene enters the background, or when its session is discarded.
    /// Use this method to release any resources associated with the scene that can be recreated when the scene reconnects.
    func sceneDidDisconnect(_ scene: UIScene) {
        // Called as the scene is being released by the system.
        // This occurs shortly after the scene enters the background, or when its session is discarded.
        // Release any resources associated with this scene that can be re-created the next time the scene connects.
        // The scene may re-connect later, as its session was not necessarily discarded (see `application:didDiscardSceneSessions` instead).
        // Resources associated with the scene should be released here
        // The scene may reconnect later, as its session was not necessarily discarded
    }

    /// Called when the scene transitions from an inactive state to an active state
    /// - Parameter scene: The scene that became active
    /// Use this method to restart any tasks that were paused (or not yet started) when the scene was inactive
    func sceneDidBecomeActive(_ scene: UIScene) {
        // Called when the scene has moved from an inactive state to an active state.
        // Use this method to restart any tasks that were paused (or not yet started) when the scene was inactive.
        // Restart any tasks that were paused while the scene was inactive

    }

    /// Called when the scene is about to move from an active state to an inactive state
    /// - Parameter scene: The scene that will resign active
    /// This may occur due to temporary interruptions (for example, an incoming phone call)
    func sceneWillResignActive(_ scene: UIScene) {
        // Called when the scene will move from an active state to an inactive state.
        // This may occur due to temporary interruptions (ex. an incoming phone call).
        // Pause any ongoing tasks or disable any timers
    }

    /// Called as the scene transitions from the background to the foreground
    /// - Parameter scene: The scene that will enter the foreground
    /// Use this method to undo the changes made when entering the background
    func sceneWillEnterForeground(_ scene: UIScene) {
        // Called as the scene transitions from the background to the foreground.
        // Use this method to undo the changes made on entering the background.
        // Undo any changes made when entering the background

    }

    /// Called as the scene transitions from the foreground to the background
    /// - Parameter scene: The scene that will enter the background
    /// Use this method to save data, release shared resources, and store enough scene-specific state information
    /// to restore the scene back to its current state
    func sceneDidEnterBackground(_ scene: UIScene) {
        // Called as the scene transitions from the foreground to the background.
        // Use this method to save data, release shared resources, and store enough scene-specific state information
        // to restore the scene back to its current state.
        // Save data and release shared resources
    }
}
