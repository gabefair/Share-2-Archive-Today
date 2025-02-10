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
    /// The window associated with the scene
    var window: UIWindow?

    /// Called when a new scene is being configured
    /// - Parameters:
    ///   - scene: The scene that is being configured
    ///   - session: The session containing scene details
    ///   - connectionOptions: Options for configuring the scene connection
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let _ = (scene as? UIWindowScene) else { return }
    }

    /// Handles continuing user activity in the scene
    /// - Parameters:
    ///   - scene: The scene handling the activity
    ///   - userActivity: The activity to continue
    /// Opens the URL in archive.today if it's a valid share2archivetoday URL
    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        guard let url = userActivity.webpageURL,
              url.scheme == "share2archivetoday",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
              let urlQueryItem = components.queryItems?.first(where: { $0.name == "url" }),
              let urlString = urlQueryItem.value,
              let archiveUrl = URL(string: "https://archive.today/?run=1&url=\(urlString)") else {
            return
        }
        
        UIApplication.shared.open(archiveUrl)
    }
    
    /// Handles opening URLs in the scene
    /// - Parameters:1) 1
    ///   - scene: The scene handling the URLs
    ///   - URLContexts: Set of URL contexts to handle
    /// Opens the URL in archive.today if it's a valid share2archivetoday URL
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard let url = URLContexts.first?.url,
              url.scheme == "share2archivetoday",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
              let urlQueryItem = components.queryItems?.first(where: { $0.name == "url" }),
              let urlString = urlQueryItem.value,
              let archiveUrl = URL(string: "https://archive.today/?run=1&url=\(urlString)") else {
            return
        }
        
        UIApplication.shared.open(archiveUrl)
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
