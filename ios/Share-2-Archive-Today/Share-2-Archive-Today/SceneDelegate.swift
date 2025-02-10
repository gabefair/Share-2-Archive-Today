//
//  SceneDelegate.swift
//  Share-2-Archive-Today
//
//  Created by Gabirel Fair on 2/9/25.
//

import UIKit
import SafariServices

class SceneDelegate: UIResponder, UIWindowSceneDelegate {

    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let _ = (scene as? UIWindowScene) else { return }
    }

    func scene(_ scene: UIScene, continue userActivity: NSUserActivity) {
        guard let url = userActivity.webpageURL,
              url.scheme == "share2archivetoday",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
              let urlQueryItem = components.queryItems?.first(where: { $0.name == "url" }),
              let urlString = urlQueryItem.value,
              let decodedUrl = urlString.removingPercentEncoding,
              let archiveUrl = URL(string: "https://archive.today/?run=1&url=\(urlString)") else {
            return
        }
        
        // Present the archive.today URL in Safari
        if let windowScene = scene as? UIWindowScene,
           let viewController = windowScene.windows.first?.rootViewController {
            let safariVC = SFSafariViewController(url: archiveUrl)
            viewController.present(safariVC, animated: true, completion: nil)
        }
    }

    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        guard let url = URLContexts.first?.url,
              url.scheme == "share2archivetoday",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
              let urlQueryItem = components.queryItems?.first(where: { $0.name == "url" }),
              let urlString = urlQueryItem.value,
              let decodedUrl = urlString.removingPercentEncoding,
              let archiveUrl = URL(string: "https://archive.today/?run=1&url=\(urlString)") else {
            return
        }
        
        // Present the archive.today URL in Safari
        if let windowScene = scene as? UIWindowScene,
           let viewController = windowScene.windows.first?.rootViewController {
            let safariVC = SFSafariViewController(url: archiveUrl)
            viewController.present(safariVC, animated: true, completion: nil)
        }
    }

    func sceneDidDisconnect(_ scene: UIScene) {
        // Called as the scene is being released by the system.
        // This occurs shortly after the scene enters the background, or when its session is discarded.
        // Release any resources associated with this scene that can be re-created the next time the scene connects.
        // The scene may re-connect later, as its session was not necessarily discarded (see `application:didDiscardSceneSessions` instead).
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        // Called when the scene has moved from an inactive state to an active state.
        // Use this method to restart any tasks that were paused (or not yet started) when the scene was inactive.
    }

    func sceneWillResignActive(_ scene: UIScene) {
        // Called when the scene will move from an active state to an inactive state.
        // This may occur due to temporary interruptions (ex. an incoming phone call).
    }

    func sceneWillEnterForeground(_ scene: UIScene) {
        // Called as the scene transitions from the background to the foreground.
        // Use this method to undo the changes made on entering the background.
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        // Called as the scene transitions from the foreground to the background.
        // Use this method to save data, release shared resources, and store enough scene-specific state information
        // to restore the scene back to its current state.
    }
}
