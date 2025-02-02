
import UIKit

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Create a window that's the same size as the screen
        window = UIWindow(frame: UIScreen.main.bounds)
        
        // Create our simple view controller that explains the app's purpose
        let viewController = ViewController()
        viewController.view.backgroundColor = .systemBackground
        
        // Set it as the root of our window
        window?.rootViewController = viewController
        
        // Make the window visible
        window?.makeKeyAndVisible()
        
        return true
    }
}
