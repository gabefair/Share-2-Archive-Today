// Share-2-Archive-Today/URLShareExtension/ShareViewController.swift

import UIKit
import Social
import os.log
import MobileCoreServices

/// A view controller that handles the share extension functionality for archiving URLs
/// This implementation follows a bottom sheet presentation style with proper error handling
@objc(ShareViewController)
class ShareViewController: UIViewController {
    
    // MARK: - Outlets
    
    @IBOutlet weak var bottomSheetView: UIView!
    @IBOutlet weak var titleLabel: UILabel!
    @IBOutlet weak var closeButton: UIButton!
    @IBOutlet weak var urlLabel: UILabel!
    @IBOutlet weak var archiveButton: UIButton!
    
    // MARK: - Properties
    
    /// The URL string to be archived
    private var urlString: String = ""
    
    /// Logger for debugging
    private let logger = Logger(subsystem: "org.Gnosco.Share-2-Archive-Today", category: "ShareExtension")
    
    /// URL store for saving URLs
    private lazy var urlStore = URLStore.shared
    
    // MARK: - Lifecycle Methods
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        logger.info("ShareViewController loaded")
        setupUI()
        extractSharedContent()
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        
        animateBottomSheet()
    }
}

// MARK: - Private Methods

private extension ShareViewController {
    
    func setupUI() {
        view.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        
        // Ensure the button is properly styled
        archiveButton.layer.cornerRadius = 12
        archiveButton.isEnabled = false
        
        // Set close button tint
        closeButton.tintColor = .systemGray
        
        logger.debug("UI setup complete")
    }
    
    func animateBottomSheet() {
        // Position the sheet off-screen initially
        let originalY = bottomSheetView.frame.origin.y
        bottomSheetView.frame.origin.y = view.bounds.height
        
        // Animate it up
        UIView.animate(withDuration: 0.3, delay: 0, options: .curveEaseOut) {
            self.bottomSheetView.frame.origin.y = originalY
        }
        
        logger.debug("Bottom sheet animation started")
    }
    
    func extractSharedContent() {
        guard let extensionItems = extensionContext?.inputItems as? [NSExtensionItem] else {
            handleError(message: "No shared content found")
            return
        }
        
        logger.info("Processing \(extensionItems.count) extension items")
        var foundURL = false
        
        // Process all extension items
        for extensionItem in extensionItems {
            guard let attachments = extensionItem.attachments else { continue }
            
            // Process all attachments
            for attachment in attachments {
                // Try different types of content
                processAttachment(attachment)
                foundURL = !urlString.isEmpty
                if foundURL { break }
            }
            if foundURL { break }
        }
        
        // If we still haven't found a URL, check after a delay
        if !foundURL {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
                guard let self = self, self.urlString.isEmpty else { return }
                self.handleError(message: "Unable to extract URL from shared content")
            }
        }
    }
    
    func processAttachment(_ attachment: NSItemProvider) {
        // Check for URL
        if attachment.hasItemConformingToTypeIdentifier(kUTTypeURL as String) {
            attachment.loadItem(forTypeIdentifier: kUTTypeURL as String, options: nil) { [weak self] (item, error) in
                self?.handleLoadedItem(item, error: error)
            }
            return
        }
        
        // Check for web URL specifically
        if attachment.hasItemConformingToTypeIdentifier("public.url") {
            attachment.loadItem(forTypeIdentifier: "public.url", options: nil) { [weak self] (item, error) in
                self?.handleLoadedItem(item, error: error)
            }
            return
        }
        
        if attachment.hasItemConformingToTypeIdentifier("com.apple.safari.safariPageSnapshot") {
            attachment.loadItem(forTypeIdentifier: "com.apple.safari.safariPageSnapshot", options: nil) { [weak self] (item, error) in
                // Process Safari snapshot from iPad
                if let dictionary = item as? [String: Any],
                   let urlString = dictionary["URL"] as? String {
                    DispatchQueue.main.async {
                        self?.updateUI(with: urlString)
                    }
                }
            }
            return
        }
        
        // Check for web page with URL
        if attachment.hasItemConformingToTypeIdentifier("com.apple.webpageURL") {
            attachment.loadItem(forTypeIdentifier: "com.apple.webpageURL", options: nil) { [weak self] (item, error) in
                self?.handleLoadedItem(item, error: error)
            }
            return
        }
        
        // Check for Safari bookmark
        if attachment.hasItemConformingToTypeIdentifier("com.apple.safari.bookmark") {
            attachment.loadItem(forTypeIdentifier: "com.apple.safari.bookmark", options: nil) { [weak self] (item, error) in
                if let bookmarkDict = item as? [String: Any],
                   let urlString = bookmarkDict["URL"] as? String {
                    DispatchQueue.main.async {
                        self?.updateUI(with: urlString)
                    }
                } else {
                    self?.handleLoadedItem(item, error: error)
                }
            }
            return
        }
        
        // Check for HTML content
        if attachment.hasItemConformingToTypeIdentifier(kUTTypeHTML as String) {
            attachment.loadItem(forTypeIdentifier: kUTTypeHTML as String, options: nil) { [weak self] (item, error) in
                DispatchQueue.main.async {
                    guard let self = self else { return }
                    
                    if let htmlString = item as? String,
                       let url = self.extractURLFromHTML(htmlString) {
                        self.updateUI(with: url.absoluteString)
                    } else if let data = item as? Data,
                              let htmlString = String(data: data, encoding: .utf8),
                              let url = self.extractURLFromHTML(htmlString) {
                        self.updateUI(with: url.absoluteString)
                    } else if let error = error {
                        self.logger.error("Error loading HTML: \(error.localizedDescription)")
                    }
                }
            }
            return
        }
        
        // Check for text that might contain a URL
        if attachment.hasItemConformingToTypeIdentifier(kUTTypePlainText as String) {
            attachment.loadItem(forTypeIdentifier: kUTTypePlainText as String, options: nil) { [weak self] (item, error) in
                DispatchQueue.main.async {
                    guard let self = self else { return }
                    
                    if let text = item as? String {
                        // Try to extract a URL from the text
                        if let url = self.extractURLFromText(text) {
                            self.updateUI(with: url.absoluteString)
                        } else {
                            self.logger.warning("Text content does not contain a valid URL: \(text)")
                        }
                    } else if let error = error {
                        self.logger.error("Error loading text: \(error.localizedDescription)")
                    }
                }
            }
            return
        }
        
        // Try as a file URL that might point to a webpage
        if attachment.hasItemConformingToTypeIdentifier(kUTTypeFileURL as String) {
            attachment.loadItem(forTypeIdentifier: kUTTypeFileURL as String, options: nil) { [weak self] (item, error) in
                self?.handleLoadedItem(item, error: error)
            }
            return
        }
    }
    
    func handleLoadedItem(_ item: Any?, error: Error?) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            if let url = item as? URL {
                self.updateUI(with: url.absoluteString)
            } else if let urlString = item as? String, let url = URL(string: urlString) {
                self.updateUI(with: url.absoluteString)
            } else if let data = item as? Data, let urlString = String(data: data, encoding: .utf8),
                      let url = URL(string: urlString) {
                self.updateUI(with: url.absoluteString)
            } else if let error = error {
                self.logger.error("Error loading URL: \(error.localizedDescription)")
            }
        }
    }
    
    func extractURLFromText(_ text: String) -> URL? {
        // Try to create a URL directly
        if let url = URL(string: text), url.scheme != nil && url.host != nil {
            return url
        }
        
        // Try to detect URLs using NSDataDetector
        let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)
        let matches = detector?.matches(in: text, options: [], range: NSRange(location: 0, length: text.utf16.count))
        
        if let match = matches?.first, let url = match.url {
            return url
        }
        
        return nil
    }
    
    /// Attempts to extract a URL from HTML content
    /// - Parameter html: The HTML string to parse
    /// - Returns: URL if found, nil otherwise
    private func extractURLFromHTML(_ html: String) -> URL? {
        // Try to find an og:url meta tag
        if let range = html.range(of: "property=\"og:url\" content=\""),
           let endRange = html.range(of: "\"", range: range.upperBound..<html.endIndex) {
            let urlString = String(html[range.upperBound..<endRange.lowerBound])
            if let url = URL(string: urlString) {
                return url
            }
        }
        
        // Try to find a canonical link
        if let range = html.range(of: "rel=\"canonical\" href=\""),
           let endRange = html.range(of: "\"", range: range.upperBound..<html.endIndex) {
            let urlString = String(html[range.upperBound..<endRange.lowerBound])
            if let url = URL(string: urlString) {
                return url
            }
        }
        
        // Use URL detector as fallback
        return extractURLFromText(html)
    }
    
    func updateUI(with urlString: String) {
        self.urlString = urlString
        self.urlLabel.text = urlString
        self.archiveButton.isEnabled = true
        self.logger.info("URL loaded: \(urlString)")
    }
    
    func handleError(message: String) {
        logger.error("Error: \(message)")
        
        let alert = UIAlertController(
            title: "Error",
            message: message,
            preferredStyle: .alert
        )
        
        alert.addAction(UIAlertAction(title: "OK", style: .default) { [weak self] _ in
            self?.dismissShareSheet()
        })
        
        present(alert, animated: true)
    }
    
    func dismissShareSheet() {
        logger.info("Dismissing share sheet")
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }
    
    func saveAndRedirectToApp() {
        guard !self.urlString.isEmpty else {
            logger.error("URL string is empty")
            return
        }
        
        // 1. Save the URL to URLStore so the main app can access it
        let saveResult = urlStore.saveURL(self.urlString)
        logger.info("URL saved to store: \(saveResult)")
        
        // 2. Set a flag in UserDefaults to indicate a URL needs processing
        if let defaults = UserDefaults(suiteName: "group.org.Gnosco.Share-2-Archive-Today") {
            defaults.set(true, forKey: "pendingArchiveURL")
            defaults.set(self.urlString, forKey: "lastSharedURL")
            defaults.synchronize() // Force immediate write
            logger.info("Pending archive flag set in UserDefaults")
        }
        
        // 3. Prepare encoded URL for custom scheme
        let encodedUrl = self.urlString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        
        // 4. Create URL for main app
        if let appUrl = URL(string: "share2archivetoday://?url=\(encodedUrl)") {
            logger.info("Redirecting to main app with URL: \(appUrl)")
            
            // Attempt to open the main app first
            self.extensionContext?.open(appUrl, completionHandler: { [weak self] success in
                self?.logger.info("Open main app success: \(success)")
                
                // Then complete the extension's request
                self?.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
            })
        } else {
            logger.error("Failed to create app URL")
            dismissShareSheet()
        }
    }

    
    // MARK: - Action Methods
    
    @IBAction func closeButtonTapped(_ sender: Any) {
        logger.info("Close button tapped")
        UIView.animate(withDuration: 0.3, animations: {
            self.bottomSheetView.frame.origin.y = self.view.bounds.height
        }) { _ in
            self.dismissShareSheet()
        }
    }
    
    @IBAction func archiveButtonTapped(_ sender: Any) {
        logger.info("Archive button tapped for URL: \(self.urlString)")
        
        // Disable the button to prevent multiple taps
        archiveButton.isEnabled = false
        
        // Show a brief success message
        let feedbackAlert = UIAlertController(
            title: "URL Archived",
            message: "Opening Archive Today app...",
            preferredStyle: .alert
        )
        
        present(feedbackAlert, animated: true)
        
        // Dismiss after a short delay and redirect to main app
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [weak self] in
            feedbackAlert.dismiss(animated: true) {
                self?.saveAndRedirectToApp()
            }
        }
    }
}
