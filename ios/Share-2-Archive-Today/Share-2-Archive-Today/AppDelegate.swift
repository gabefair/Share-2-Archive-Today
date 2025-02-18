//
//  AppDelegate.swift
//  Share-2-Archive-Today
//
//  Created by Gabirel Fair on 2/17/25.
//

import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {

        /**
     Called when the app has finished launching.
     
     - Parameter application: The singleton app object.
     - Parameter launchOptions: A dictionary indicating the reason the app was launched.
     - Returns: A Boolean indicating whether the app launched successfully.
     */
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Place any global app configuration here.
        return true
    }

    // MARK: UISceneSession Lifecycle
    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        // Called when a new scene session is being created.
        // Use this method to select a configuration to create the new scene with.
        // Return a configuration to create the new scene with.
        return UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }

    func application(_ application: UIApplication, didDiscardSceneSessions sceneSessions: Set<UISceneSession>) {
        // Called when the user discards a scene session.
        // If any sessions were discarded while the application was not running, this will be called shortly after application:didFinishLaunchingWithOptions.
        // Use this method to release any resources that were specific to the discarded scenes, as they will not return.
    }


}

