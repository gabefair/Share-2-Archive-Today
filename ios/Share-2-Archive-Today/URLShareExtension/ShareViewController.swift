//  ShareViewController.swift
//  URLShareExtension
import UIKit
import Social
import UniformTypeIdentifiers
import MobileCoreServices
import SafariServices
import os.log
import OSLog

/// Represents possible errors that can occur during share extension processing
enum ShareExtensionError: Error {
    case noInputItems
    case invalidInputItems
    case noValidURLFound
    case urlProcessingFailed
    case archiveURLCreationFailed
    case safariViewControllerFailed
    
    var localizedDescription: String {
        switch self {
        case .noInputItems:
            return "No input items found in share extension"
        case .invalidInputItems:
            return "Invalid input items format"
        case .noValidURLFound:
            return "No valid URL found in shared content"
        case .urlProcessingFailed:
            return "Failed to process URL"
        case .archiveURLCreationFailed:
            return "Failed to create archive.today URL"
        case .safariViewControllerFailed:
            return "Failed to present Safari view"
        }
    }
}

/// A view controller that handles the share extension functionality for archiving URLs
/// This controller manages the sharing interface and processes URLs for archiving with enhanced error handling
class ShareViewController: UIViewController {
    
    /// Text view for optional user notes
    @IBOutlet weak var textView: UITextView!
    
    /// Activity indicator to show loading state
    private lazy var activityIndicator: UIActivityIndicatorView = {
        let indicator = UIActivityIndicatorView(style: .medium)
        indicator.hidesWhenStopped = true
        return indicator
    }()
    
    /// Logger for debugging and monitoring
    private let logger = Logger(subsystem: "org.Gnosco.Share-2-Archive-Today", category: "ShareExtension")
    
    
    
    /// Shared instance of URLStore for managing saved URLs
    private let urlStore = URLStore.shared
    
    // MARK: - Lifecycle Methods
    
    /// Sets up the initial state of the view controller
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        processSharedContent()
    }
    
    /// Configures the UI elements of the view controller
    private func setupUI() {
        textView?.isHidden = true
        
        // Add activity indicator
        view.addSubview(activityIndicator)
        activityIndicator.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            activityIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            activityIndicator.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }
    
    // MARK: - Action Methods
    
    /// Handles the cancellation of the share extension
    /// - Parameter sender: The object that initiated the action
    @IBAction func cancelTapped(_ sender: Any) {
        logger.debug("Share extension cancelled by user")
        completeRequest()
    }
    
    /// Called when the user taps the done button
    /// - Parameter sender: The object that initiated the action
    @IBAction func doneTapped(_ sender: Any) {
        logger.debug("Share extension completed by user")
        completeRequest()
    }
    
    // MARK: - URL Processing Methods
    
    /// Processes an archive.today URL to extract the original URL if present
    /// - Parameter urlString: The URL string to process
    /// - Returns: The processed URL string
    private func processArchiveUrl(_ urlString: String) -> String {
        guard let url = URL(string: urlString),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            logger.error("Failed to process URL: \(urlString)")
            return urlString
        }
        
        if let host = components.host, host.contains("archive"),
           let lastPathComponent = components.path.split(separator: "/").last {
            return String(lastPathComponent)
        }
        
        return url.absoluteString
    }
    
    /// Determines if a URL parameter is used for tracking
    /// - Parameter param: The parameter name to check
    /// - Returns: True if the parameter is a tracking parameter
    private func isTrackingParam(_ param: String) -> Bool {
        let trackingParams: Set<String> = [
            "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term"
        ]
        return trackingParams.contains(param)
    }
    
    /// Determines if a parameter is an unwanted YouTube-specific parameter
    /// - Parameter param: The parameter name to check
    /// - Returns: True if the parameter should be removed
    private func isUnwantedYoutubeParam(_ param: String) -> Bool {
        return param == "feature"
    }
    
    /// Removes tracking parameters and normalizes URLs for specific services
    /// - Parameter urlString: The URL string to clean
    /// - Returns: A cleaned URL string
    private func cleanTrackingParamsFromUrl(_ urlString: String) -> String {
        guard let url = URL(string: urlString),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            return urlString
        }
        
        var newComponents = components
        
        // Handle specific services (YouTube, Substack)
        if let host = components.host, host.contains("youtube.com") || host.contains("youtu.be") {
            newComponents.host = host.replacingOccurrences(of: "music.", with: "")
            newComponents.path = components.path.replacingOccurrences(of: "/shorts/", with: "/v/")
        }
        
        // Handle Substack URLs
        if let host = components.host, host.hasSuffix(".substack.com") {
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
    private func processSharedContent() {
        activityIndicator.startAnimating()
        
        guard let extensionItem = extensionContext?.inputItems.first as? NSExtensionItem,
              let attachments = extensionItem.attachments else {
            handleError(ShareExtensionError.noInputItems)
            return
        }
        
        for itemProvider in attachments {
            if itemProvider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                processItemProvider(itemProvider)
                return
            }
        }
        
        handleError(ShareExtensionError.noValidURLFound)
    }
    
    /// Processes a single item provider to extract and handle the URL
    /// - Parameter itemProvider: The item provider containing the URL
    private func processItemProvider(_ itemProvider: NSItemProvider) {
        itemProvider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] (item, error) in
            guard let self = self else { return }
            
            if let error = error {
                self.logger.error("Error loading shared item: \(error.localizedDescription)")
                self.handleError(error)
                return
            }
            
            var urlString: String?
            if let url = item as? URL {
                urlString = url.absoluteString
            } else if let urlStr = item as? String {
                urlString = urlStr
            }
            
            guard let urlStr = urlString else {
                self.handleError(ShareExtensionError.noValidURLFound)
                return
            }
            
            let processedUrl = self.processArchiveUrl(urlStr)
            let cleanedUrl = self.cleanTrackingParamsFromUrl(processedUrl)
            
            // Save the URL to shared storage
            self.urlStore.saveURL(cleanedUrl)
            
            DispatchQueue.main.async {
                guard let encodedUrl = cleanedUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                      let archiveUrl = URL(string: "https://archive.today/?run=1&url=\(encodedUrl)") else {
                    self.handleError(ShareExtensionError.archiveURLCreationFailed)
                    return
                }
                
                // Present the SFSafariViewController
                let safariVC = SFSafariViewController(url: archiveUrl)
                safariVC.preferredBarTintColor = .systemBackground
                safariVC.preferredControlTintColor = .systemBlue
                safariVC.dismissButtonStyle = .close
                
                self.present(safariVC, animated: true) {
                    // Complete the extension request after a short delay
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                        self.activityIndicator.stopAnimating()
                        self.completeRequest()
                    }
                }
            }
        }
    }
    
    /// Handles errors that occur during the share extension process
    /// - Parameter error: The error to handle
    private func handleError(_ error: Error) {
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                
                self.activityIndicator.stopAnimating()
                
                let message: String
                if let shareError = error as? ShareExtensionError {
                    message = shareError.localizedDescription
                } else {
                    message = error.localizedDescription
                }
                
                self.logger.error("Share extension error: \(message)")
                
                #if DEBUG
                // In debug builds, show both the error and recent logs
                let debugMessage = """
                Error: \(message)
                
                Recent Logs:
                \(self.collectRecentLogs())
                """
                self.showDebugLog(debugMessage)
                #else
                // In release builds, show only the user-facing error
                let alert = UIAlertController(
                    title: "Error",
                    message: message,
                    preferredStyle: .alert
                )
                alert.addAction(UIAlertAction(title: "OK", style: .default) { [weak self] _ in
                    self?.completeRequest()
                })
                self.present(alert, animated: true)
                #endif
            }
        }
    
    /// Completes the share extension request
    private func completeRequest() {
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
}

// MARK: - UITextView Extension

extension UITextView {
    /// A placeholder string that appears when the text view is empty
    @IBInspectable
    var placeholder: String? {
        get { return nil }
        set {
            text = newValue
            textColor = .placeholderText
            
            // Remove any existing tap gesture recognizers to avoid duplicates
            gestureRecognizers?.removeAll(where: { $0 is UITapGestureRecognizer })
            
            let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleTap))
            addGestureRecognizer(tapGesture)
        }
    }
    
    /// Handles tap gesture on the text view
    @objc private func handleTap() {
        if textColor == .placeholderText {
            text = ""
            textColor = .label
        }
        becomeFirstResponder()
    }
}

#if DEBUG
extension ShareViewController {
    /// Debug helper to show logs in the UI during development
    private func showDebugLog(_ message: String) {
        let debugAlert = UIAlertController(
            title: "Debug Log",
            message: message,
            preferredStyle: .alert
        )
        debugAlert.addAction(UIAlertAction(title: "OK", style: .default))
        
        DispatchQueue.main.async { [weak self] in
            self?.present(debugAlert, animated: true)
        }
    }
    
    /// Collect recent logs for the app
    private func collectRecentLogs() -> String {
            // Create log store
            guard let logStore = try? OSLogStore(scope: .currentProcessIdentifier) else {
                return "Could not access log store"
            }
            
            do {
                // Create enumerator for the log store
                let enumerator = try logStore.enumerate() //ERROR here: Value of type 'OSLogStore' has no member 'enumerate'
                var entries: [String] = []
                
                // Enumerate through log entries
                while let entry = try enumerator.next() as? OSLogEntryLog {
                    // Only include entries from our subsystem
                    if entry.subsystem == "org.Gnosco.Share-2-Archive-Today" {
                        let timestamp = entry.date.formatted()
                        entries.append("[\(timestamp)] \(entry.composedMessage)")
                    }
                }
                
                return entries.isEmpty ? "No recent logs found" : entries.joined(separator: "\n")
            } catch {
                return "Error collecting logs: \(error.localizedDescription)"
            }
        }
}
#endif
