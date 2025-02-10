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
        textView?.placeholder = "Add a note (optional)"
    }

    /// Handles the cancellation of the share extension
    /// - Parameter sender: The object that initiated the action
    @IBAction func cancelTapped(_ sender: Any) {
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
    
    /// Processes the shared content when the user taps done
    /// - Parameter sender: The object that initiated the action
    @IBAction func doneTapped(_ sender: Any) {
        processSharedContent()
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
        
        let group = DispatchGroup()
        var foundURL = false
        
        for itemProvider in attachments {
            if itemProvider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                group.enter()
                
                itemProvider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] (item, error) in
                    defer { group.leave() }
                    
                    guard let self = self else { return }
                    
                    var urlString: String?
                    
                    if let url = item as? URL {
                        urlString = url.absoluteString
                    } else if let urlStr = item as? String {
                        urlString = urlStr
                    }
                    
                    if let urlStr = urlString {
                        foundURL = true
                        let processedUrl = self.processArchiveUrl(urlStr)
                        let cleanedUrl = self.cleanTrackingParamsFromUrl(processedUrl)
                        
                        URLStore.shared.saveURL(cleanedUrl)
                        
                        DispatchQueue.main.async {
                            self.openUrlInMainApp(cleanedUrl)
                        }
                    }
                }
            }
        }
        
        group.notify(queue: .main) { [weak self] in
            if !foundURL {
                let alert = UIAlertController(
                    title: "No URL Found",
                    message: "No valid URL was found in the shared content.",
                    preferredStyle: .alert
                )
                alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in
                    self?.completeRequest()
                })
                self?.present(alert, animated: true)
            }
        }
    }
    
    /// Opens a URL in the main app or falls back to Safari
    /// - Parameter urlString: The URL string to open
    /// This method attempts to open the URL in the main app first, and if that fails,
    /// falls back to opening it in Safari
    private func openUrlInMainApp(_ urlString: String) {
        guard let encodedUrl = urlString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let archiveUrl = URL(string: "https://archive.today/?run=1&url=\(encodedUrl)") else {
            completeRequest()
            return
        }
        
        // Create the URL for opening in the main app
        let mainAppUrl = URL(string: "share2archivetoday://open?url=\(encodedUrl)")
        
        if let mainAppUrl = mainAppUrl {
            // Create an NSUserActivity to open the URL in the main app
            let userActivity = NSUserActivity(activityType: "org.Gnosco.Share-2-Archive-Today.openURL")
            userActivity.webpageURL = mainAppUrl
            
            // Complete the extension request with the activity
            let item = NSExtensionItem()
            item.attachments = []
            item.userInfo = ["activity": userActivity]
            
            extensionContext?.completeRequest(returningItems: [item]) { success in
                if !success {
                    // Fallback to Safari if we can't open the main app
                    DispatchQueue.main.async { [weak self] in
                        let safariVC = SFSafariViewController(url: archiveUrl)
                        self?.present(safariVC, animated: true)
                    }
                }
            }
        } else {
            // Fallback to Safari if we can't create the main app URL
            let safariVC = SFSafariViewController(url: archiveUrl)
            present(safariVC, animated: true) { [weak self] in
                self?.completeRequest()
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
