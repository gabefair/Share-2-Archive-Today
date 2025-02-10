//
//  ShareViewController.swift
//  URLShareExtension
//
//  Created by Gabirel Fair on 2/9/25.
//

import UIKit
import Social
import UniformTypeIdentifiers
import MobileCoreServices
import SafariServices

/// A view controller that handles the share extension functionality for archiving URLs
/// This controller manages the sharing interface and processes URLs for archiving
class ShareViewController: UIViewController {
    /// Text view for optional user notes
    @IBOutlet weak var textView: UITextView!
    /// Shared instance of URLStore for managing saved URLs
    private let urlStore = URLStore.shared
    
    /// Sets up the initial state of the view controller
    override func viewDidLoad() {
        super.viewDidLoad()
        textView?.isHidden = true
        processSharedContent()
    }

    /// Handles the cancellation of the share extension
    /// - Parameter sender: The object that initiated the action
    @IBAction func cancelTapped(_ sender: Any) {
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
    
    /// Processes the shared content when the user taps done
    /// - Parameter sender: The object that initiated the action
    @IBAction func doneTapped(_ sender: Any) {
        processSharedContent() //Not called but method kept for proper lifecycle management
    }
    
    // MARK: - URL Processing Methods
    
    /// Processes an archive.today URL to extract the original URL if present
    /// - Parameter url: The URL string to process
    /// - Returns: The processed URL string, either the original URL or the input URL if processing fails
    private func processArchiveUrl(_ url: String) -> String {
        guard let url = URL(string: url),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            return url
        }
        
        if components.host?.contains("archive") == true,
           let path = components.path.split(separator: "/").last {
            return String(path)
        }
        
        return url.absoluteString
    }
    
    /// Determines if a URL parameter is used for tracking
    /// - Parameter param: The parameter name to check
    /// - Returns: True if the parameter is a tracking parameter, false otherwise
    private func isTrackingParam(_ param: String) -> Bool {
        let trackingParams: Set<String> = [
            "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
            // ... [tracking parameters list]
        ]
        return trackingParams.contains(param)
    }
    
    /// Determines if a parameter is an unwanted YouTube-specific parameter
    /// - Parameter param: The parameter name to check
    /// - Returns: True if the parameter should be removed from YouTube URLs
    private func isUnwantedYoutubeParam(_ param: String) -> Bool {
        return param == "feature"
    }
    
    /// Removes tracking parameters and normalizes URLs for specific services
    /// - Parameter urlString: The URL string to clean
    /// - Returns: A cleaned URL string with tracking parameters removed and service-specific normalizations applied
    private func cleanTrackingParamsFromUrl(_ urlString: String) -> String {
        guard let url = URL(string: urlString),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            return urlString
        }
        
        var newComponents = components
        
        // Handle specific services (YouTube, Substack)
        if components.host?.contains("youtube.com") == true || components.host?.contains("youtu.be") == true {
            newComponents.host = components.host?.replacingOccurrences(of: "music.", with: "")
            newComponents.path = components.path.replacingOccurrences(of: "/shorts/", with: "/v/")
        }
        
        // Handle Substack URLs
        if components.host?.hasSuffix(".substack.com") == true {
            var queryItems = components.queryItems ?? []
            queryItems.append(URLQueryItem(name: "no_cover", value: "true"))
            newComponents.queryItems = queryItems
        }
        
        // Filter tracking parameters
        if let queryItems = components.queryItems {
            let filteredItems = queryItems.filter { item in
                !isTrackingParam(item.name) &&
                !(components.host?.contains("youtube.com") == true && isUnwantedYoutubeParam(item.name))
            }
            newComponents.queryItems = filteredItems.isEmpty ? nil : filteredItems
        }
        
        return newComponents.url?.absoluteString ?? urlString
    }
    
    /// Processes the shared content to extract and handle URLs
    /// This method extracts URLs from the shared content, processes them, and initiates the archiving process
    private func processSharedContent() {
        guard let extensionItem = extensionContext?.inputItems.first as? NSExtensionItem,
              let attachments = extensionItem.attachments else {
            completeRequest()
            return
        }
        
        for itemProvider in attachments {
            if itemProvider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                itemProvider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] (item, error) in
                    guard let self = self else { return }
                    
                    var urlString: String?
                    if let url = item as? URL {
                        urlString = url.absoluteString
                    } else if let urlStr = item as? String {
                        urlString = urlStr
                    }
                    
                    if let urlStr = urlString {
                        let processedUrl = self.processArchiveUrl(urlStr)
                        let cleanedUrl = self.cleanTrackingParamsFromUrl(processedUrl)
                        
                        // Save URL to shared storage
                        self.urlStore.saveURL(cleanedUrl)
                        
                        // Prepare URL for main app
                        if let encodedUrl = cleanedUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                           let mainAppUrl = URL(string: "share2archivetoday://open?url=\(encodedUrl)") {
                            
                            // Create activity to pass to main app
                            let userActivity = NSUserActivity(activityType: "org.Gnosco.Share-2-Archive-Today.openURL")
                            userActivity.webpageURL = mainAppUrl
                            
                            // Create extension item with activity
                            let item = NSExtensionItem()
                            item.userInfo = ["activity": userActivity]
                            
                            // Complete request with activity
                            self.extensionContext?.completeRequest(returningItems: [item])
                        } else {
                            self.completeRequest()
                        }
                    }
                }
            }
        }
    }
    
    /// Completes the share extension request
    /// This method should be called when the extension has finished its work
    private func completeRequest() {
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
}

// MARK: - UITextView Placeholder Extension

extension UITextView {
    /// A placeholder string that appears when the text view is empty
    @IBInspectable
    var placeholder: String? {
        get {
            return nil
        }
        set {
            text = newValue
            textColor = .placeholderText
            
            if let existingGesture = gestureRecognizers?.first(where: { $0 is UITapGestureRecognizer }) {
                removeGestureRecognizer(existingGesture)
            }
            
            let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleTap))
            addGestureRecognizer(tapGesture)
        }
    }
    
    /// Handles tap gesture on the text view
    /// Removes placeholder text and changes text color when the text view is tapped
    @objc private func handleTap() {
        if textColor == .placeholderText {
            text = ""
            textColor = .label
        }
        becomeFirstResponder()
    }
}
