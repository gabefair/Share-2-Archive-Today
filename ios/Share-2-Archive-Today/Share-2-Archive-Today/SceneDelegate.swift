//
//  SceneDelegate.swift
//  Share-2-Archive-Today
//
import UIKit
import SafariServices

/// Manages the app's window scene and handles URL-based interactions.
/// This delegate is responsible for managing the app's scene lifecycle and processing URLs
/// (such as those from your share extension) to open them via archive.today.
class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    
    /// The window associated with the scene.
    var window: UIWindow?
    
    /// Called when a new scene is being configured.
    /// - Parameters:
    ///   - scene: The scene that is being configured.
    ///   - session: The session containing scene details.
    ///   - connectionOptions: Options for configuring the scene connection.
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let _ = (scene as? UIWindowScene) else { return }
    }
    
    /// Handles continuing a user activity (e.g., from Handoff) in the scene.
    /// Extracts the URL from the user activity and, if it matches the expected scheme,
    /// opens the corresponding archive.today URL.
    /// - Parameters:
    ///   - scene: The scene handling the activity.
    ///   - userActivity: The user activity to continue.
    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        if let url = userActivity.webpageURL {
            handleShare2ArchiveTodayURL(url)
        }
    }
    
    /// Handles opening URLs in the scene.
    /// Processes incoming URL contexts and, if they match the "share2archivetoday" scheme,
    /// opens the corresponding archive.today URL.
    /// - Parameters:
    ///   - scene: The scene handling the URL.
    ///   - URLContexts: A set of URL contexts to handle.
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard let context = URLContexts.first else { return }
        handleShare2ArchiveTodayURL(context.url)
    }
    
    /// A helper method to process URLs with the "share2archivetoday" scheme.
    /// - Parameter url: The incoming URL to process.
    /// The method checks if the URL has the "share2archivetoday" scheme, extracts the original URL
    /// from its query parameters, constructs an archive.today URL, and opens it using the system.
    private func handleShare2ArchiveTodayURL(_ url: URL) {
        guard url.scheme == "share2archivetoday",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
              let urlQueryItem = components.queryItems?.first(where: { $0.name == "url" }),
              let urlString = urlQueryItem.value,
              let encodedUrl = urlString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let archiveUrl = URL(string: "https://archive.today/?run=1&url=\(encodedUrl)") else {
            return
        }
        UIApplication.shared.open(archiveUrl)
    }
    
    /// Called when the scene is being released by the system.
    /// Use this method to release any resources associated with the scene that can be recreated later.
    /// - Parameter scene: The scene that is being released.
    func sceneDidDisconnect(_ scene: UIScene) {
        // Release any resources associated with this scene.
    }
    
    /// Called when the scene transitions from an inactive state to an active state.
    /// Use this method to restart any tasks that were paused when the scene was inactive.
    /// - Parameter scene: The scene that became active.
    func sceneDidBecomeActive(_ scene: UIScene) {
        // Restart any paused tasks.
    }
    
    /// Called when the scene is about to move from an active state to an inactive state.
    /// This may occur due to temporary interruptions (e.g., an incoming phone call).
    /// - Parameter scene: The scene that will resign active.
    func sceneWillResignActive(_ scene: UIScene) {
        // Pause ongoing tasks.
    }
    
    /// Called as the scene transitions from the background to the foreground.
    /// Use this method to undo the changes made when entering the background.
    /// - Parameter scene: The scene that will enter the foreground.
    func sceneWillEnterForeground(_ scene: UIScene) {
        // Undo changes made on entering the background.
    }
    
    /// Called as the scene transitions from the foreground to the background.
    /// Use this method to save data, release shared resources, and store enough state information
    /// to restore the scene back to its current state.
    /// - Parameter scene: The scene that will enter the background.
    func sceneDidEnterBackground(_ scene: UIScene) {
        // Save data and release shared resources.
    }
}
