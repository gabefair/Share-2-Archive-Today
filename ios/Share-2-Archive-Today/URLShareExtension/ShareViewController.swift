//
//  ShareViewController.swift
//  URLShareExtension
//
//  Created by Gabirel Fair on 2/9/25.
//

import UIKit
import UniformTypeIdentifiers

/// A view controller that handles the share extension functionality for archiving URLs
class ShareViewController: UIViewController {
    /// Text view for optional user notes
    @IBOutlet weak var textView: UITextView!
    /// Shared instance of URLStore for managing saved URLs
    private let urlStore = URLStore.shared
    
    /// Sets up the initial state of the view controller and immediately processes content
    override func viewDidLoad() {
        super.viewDidLoad()
        textView?.isHidden = true
        
        // Immediately process shared content and dismiss
        handleSharedContent()
    }

    /// Handles the cancellation of the share extension
    /// - Parameter sender: The object that initiated the action
    @IBAction func cancelTapped(_ sender: Any) {
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }
    
    /// Handles saving content when the user taps done
    /// - Parameter sender: The object that initiated the action
    @IBAction func doneTapped(_ sender: Any) {
        // Since we're handling everything in viewDidLoad, this is just a fallback
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }
    
    // MARK: - URL Processing Methods
    
    /// Processes the shared content and immediately dismisses the share sheet
    private func handleSharedContent() {
        // Get the URL from the extension context
        guard let item = extensionContext?.inputItems.first as? NSExtensionItem,
              let itemProvider = item.attachments?.first,
              itemProvider.hasItemConformingToTypeIdentifier(UTType.url.identifier) else {
            // No valid URL, dismiss immediately
            extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
            return
        }
        
        // Start URL loading and immediately dismiss the share sheet
        itemProvider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] (item, error) in
            guard let self = self,
                  let url = item as? URL ?? (item as? String).flatMap(URL.init) else {
                return
            }
            
            // Process URL in background
            DispatchQueue.global(qos: .userInitiated).async {
                // Clean and save the URL
                let cleanedUrl = self.cleanURL(url.absoluteString)
                self.urlStore.saveURL(cleanedUrl)
                
                // Create URL scheme for main app
                if let encodedUrl = cleanedUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                   let urlScheme = URL(string: "share2archivetoday://open?url=\(encodedUrl)") {
                    
                    let userActivity = NSUserActivity(activityType: "org.Gnosco.Share-2-Archive-Today.openURL")
                    userActivity.webpageURL = urlScheme
                    
                    let outputItem = NSExtensionItem()
                    outputItem.userInfo = ["activity": userActivity]
                    
                    // Complete with the output item
                    DispatchQueue.main.async {
                        self.extensionContext?.completeRequest(returningItems: [outputItem], completionHandler: nil)
                    }
                }
            }
        }
        
        // Immediately dismiss the share sheet
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }
    
    /// Cleans and normalizes the provided URL string
    /// - Parameter urlString: The URL string to clean
    /// - Returns: A cleaned URL string with tracking parameters removed
    private func cleanURL(_ urlString: String) -> String {
        guard let url = URL(string: urlString),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            return urlString
        }
        
        var newComponents = components
        
        // Handle YouTube URLs
        if components.host?.contains("youtube.com") == true || components.host?.contains("youtu.be") == true {
            newComponents.host = components.host?.replacingOccurrences(of: "music.", with: "")
            newComponents.path = components.path.replacingOccurrences(of: "/shorts/", with: "/v/")
        }
        
        // Remove tracking parameters
        if let queryItems = components.queryItems {
            let cleanedItems = queryItems.filter { item in
                !isTrackingParam(item.name) &&
                !(components.host?.contains("youtube.com") == true && item.name == "feature")
            }
            newComponents.queryItems = cleanedItems.isEmpty ? nil : cleanedItems
        }
        
        return newComponents.url?.absoluteString ?? urlString
    }
    
    /// Determines if a parameter is used for tracking
    /// - Parameter param: The parameter name to check
    /// - Returns: True if the parameter is a tracking parameter
    private func isTrackingParam(_ param: String) -> Bool {
        let trackingParams: Set<String> = [
            "utm_source", "utm_medium", "utm_campaign",
            "utm_content", "utm_term"
        ]
        return trackingParams.contains(param)
    }
}
