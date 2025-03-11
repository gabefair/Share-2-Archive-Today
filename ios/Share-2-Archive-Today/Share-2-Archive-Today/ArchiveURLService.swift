// ArchiveURLService.swift
import UIKit
import SafariServices
import os.log

/// A service to handle URL processing and archiving through archive.today
class ArchiveURLService {
    static let shared = ArchiveURLService()
    
    /// Logger for debugging
    private let logger = Logger(subsystem: "org.Gnosco.Share-2-Archive-Today", category: "ArchiveURLService")
    
    /// URL store for saving URLs
    private let urlStore = URLStore.shared
    
    /// Private initializer for singleton pattern
    private init() {}
    
    /// Processes a URL for archiving, cleaning it and storing it
    /// - Parameter urlString: Original URL to process
    /// - Returns: The processed URL string
    func processURL(_ urlString: String, saveToStore: Bool = false) -> String {
        logger.info("Processing URL for archiving: \(urlString)")
        
        // Use the URLProcessor to clean tracking parameters
        let processedURL = URLProcessor.processURL(urlString)
        logger.info("URL processed: \(urlString) -> \(processedURL)")
        
        // Only save if explicitly requested
        if saveToStore {
            urlStore.saveURL(processedURL)
            logger.info("Processed URL saved to store: \(processedURL)")
        }
        
        return processedURL
    }
    
    func saveURL(_ urlString: String) {
        urlStore.saveURL(urlString)
        logger.info("URL saved to store: \(urlString)")
    }
    
    /// Creates an archive.today URL from a given URL string
    /// - Parameter urlString: The URL to archive
    /// - Returns: A URL for the archive.today service, if creation was successful
    func createArchiveURL(from urlString: String) -> URL? {
        guard let encodedUrl = urlString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            logger.error("Failed to encode URL: \(urlString)")
            return nil
        }
        
        let archiveUrlString = "https://archive.today/?run=1&url=\(encodedUrl)"
        logger.debug("Created archive URL: \(archiveUrlString)")
        return URL(string: archiveUrlString)
    }
    
    /// Presents the archive.today page in a Safari View Controller
    /// - Parameters:
    ///   - url: The archive.today URL to present
    ///   - viewController: The view controller from which to present
    func presentArchivePage(with url: URL, from viewController: UIViewController) {
        let safariVC = SFSafariViewController(url: url)
        safariVC.preferredControlTintColor = .systemBlue
        
        logger.info("Presenting Safari View Controller with URL: \(url)")
        
        if let presented = viewController.presentedViewController {
            logger.debug("Dismissing existing presented view controller")
            presented.dismiss(animated: true) {
                viewController.present(safariVC, animated: true)
            }
        } else {
            viewController.present(safariVC, animated: true)
        }
    }
    
    /// Complete URL processing and archiving workflow
    /// - Parameters:
    ///   - urlString: The original URL string
    ///   - viewController: The view controller to present from
    /// - Returns: The processed URL string
    func processAndArchiveURL(_ urlString: String, from viewController: UIViewController) -> String {
        // Process the URL
        let processedURL = processURL(urlString)
        
        // Create and present archive.today URL
        if let archiveURL = createArchiveURL(from: processedURL) {
            presentArchivePage(with: archiveURL, from: viewController)
        } else {
            logger.error("Failed to create archive URL from: \(processedURL)")
            // Show an error alert
            let alert = UIAlertController(
                title: "Error",
                message: "Could not create archive URL",
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            viewController.present(alert, animated: true)
        }
        
        return processedURL
    }
    
    /// Extracts a URL from a URL context and processes it
    /// - Parameters:
    ///   - urlContext: The URL context containing the URL
    ///   - viewController: The view controller to present from
    func handleURLContext(_ urlContext: UIOpenURLContext, from viewController: UIViewController) {
        let incomingURL = urlContext.url
        logger.info("Received URL in context: \(incomingURL)")
        
        if incomingURL.scheme == "share2archivetoday" {
            if let components = URLComponents(url: incomingURL, resolvingAgainstBaseURL: true),
               let queryItem = components.queryItems?.first(where: { $0.name == "url" }),
               let receivedUrlString = queryItem.value,
               let decodedURL = receivedUrlString.removingPercentEncoding {
                
                // Process the URL directly from the URL parameters
                processAndArchiveURL(decodedURL, from: viewController)
            } else {
                // No URL in parameters, check if we need to process a pending URL
                checkForPendingURL(from: viewController)
            }
        } else {
            logger.error("Unsupported URL scheme: \(incomingURL.scheme ?? "nil")")
        }
    }
    
    /// Checks if there's a pending URL from the share extension that needs to be processed
    /// - Parameter viewController: The view controller to present from
    func checkForPendingURL(from viewController: UIViewController) {
        logger.info("Checking for pending URL from share extension")
        
        if let defaults = UserDefaults(suiteName: "group.org.Gnosco.Share-2-Archive-Today") {
            let hasPendingURL = defaults.bool(forKey: "pendingArchiveURL")
            
            if hasPendingURL, let urlString = defaults.string(forKey: "lastSharedURL") {
                logger.info("Found pending URL: \(urlString)")
                
                // Clear the pending flag
                defaults.set(false, forKey: "pendingArchiveURL")
                
                // Process the URL
                processAndArchiveURL(urlString, from: viewController)
            } else {
                logger.info("No pending URL found")
            }
        }
    }
}
