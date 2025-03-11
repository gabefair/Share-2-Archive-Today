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
    
    /// The processed URL string (after cleaning)
    private var processedUrlString: String = ""
    
    /// Logger for debugging
    private let logger = Logger(subsystem: "org.Gnosco.Share-2-Archive-Today", category: "ShareExtension")
    
    /// Archive URL service
    private let archiveService = ArchiveURLService.shared
    
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
    
    private func extractSharedContent() {
        // Reset the URL processing manager state at the beginning of extraction
        URLProcessingManager.shared.reset()
        
        guard let extensionItems = extensionContext?.inputItems as? [NSExtensionItem] else {
            logger.error("No shared content found")
            showToast(message: "No shared content found")
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                self.dismissShareSheet()
            }
            return
        }
        
        logger.info("Processing \(extensionItems.count) extension items")
        var processStarted = false
        
        // Process all extension items
        for extensionItem in extensionItems {
            guard let attachments = extensionItem.attachments else { continue }
            
            logger.info("Processing \(attachments.count) attachments")
            
            // Process all attachments
            for attachment in attachments {
                // Skip if we've already found a URL
                if URLProcessingManager.shared.urlFound {
                    logger.info("Skipping attachment processing as URL was already found")
                    continue
                }
                
                processAttachment(attachment)
                processStarted = true
            }
        }
        
        // If we've started processing but still don't have a URL after a delay,
        // then show an error. This gives async operations time to complete.
        if processStarted {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
                guard let self = self else { return }
                
                // Only show error and dismiss if we still don't have a URL after all processing
                // and the URLProcessingManager confirms no URL was found
                if self.urlString.isEmpty && !URLProcessingManager.shared.urlFound {
                    self.logger.warning("No URL found after processing all content")
                    self.showToast(message: "Unable to find a URL in the shared content")
                    
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                        if self.urlString.isEmpty && !URLProcessingManager.shared.urlFound {
                            self.dismissShareSheet()
                        }
                    }
                }
            }
        }
    }
    
    private func processAttachment(_ attachment: NSItemProvider) {
        // First check if a URL has already been found by another attachment processing
        if URLProcessingManager.shared.urlFound {
            logger.info("Skipping attachment processing as URL was already found")
            return
        }

        logger.info("Processing attachment with types: \(attachment.registeredTypeIdentifiers)")
        
        // First check for image types to scan for QR codes
        let imageTypes = [kUTTypeImage as String, kUTTypeJPEG as String, kUTTypePNG as String, "public.image"]
        
        for imageType in imageTypes {
            if attachment.hasItemConformingToTypeIdentifier(imageType) {
                logger.info("Found image attachment, processing for QR code")
                processImageAttachment(attachment)
                return
            }
        }
        
        // Check for URL
        if attachment.hasItemConformingToTypeIdentifier(kUTTypeURL as String) {
            logger.info("Found URL attachment")
            attachment.loadItem(forTypeIdentifier: kUTTypeURL as String, options: nil) { [weak self] (item, error) in
                self?.handleLoadedItem(item, error: error)
            }
            return
        }
        
        // Check for web URL specifically
        if attachment.hasItemConformingToTypeIdentifier("public.url") {
            logger.info("Found public.url attachment")
            attachment.loadItem(forTypeIdentifier: "public.url", options: nil) { [weak self] (item, error) in
                self?.handleLoadedItem(item, error: error)
            }
            return
        }
        
        if attachment.hasItemConformingToTypeIdentifier("com.apple.safari.safariPageSnapshot") {
            logger.info("Found Safari page snapshot")
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
            logger.info("Found web page URL")
            attachment.loadItem(forTypeIdentifier: "com.apple.webpageURL", options: nil) { [weak self] (item, error) in
                self?.handleLoadedItem(item, error: error)
            }
            return
        }
        
        // Check for Safari bookmark
        if attachment.hasItemConformingToTypeIdentifier("com.apple.safari.bookmark") {
            logger.info("Found Safari bookmark")
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
            logger.info("Found HTML content")
            attachment.loadItem(forTypeIdentifier: kUTTypeHTML as String, options: nil) { [weak self] (item, error) in
                DispatchQueue.main.async {
                    guard let self = self, !URLProcessingManager.shared.urlFound else { return }
                    
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
        
        // Check for text that might contain a URL - improved text scanning
        else if attachment.hasItemConformingToTypeIdentifier(kUTTypePlainText as String) {
            logger.info("Found plain text, checking for URLs")
            attachment.loadItem(forTypeIdentifier: kUTTypePlainText as String, options: nil) { [weak self] (item, error) in
                DispatchQueue.main.async {
                    guard let self = self, !URLProcessingManager.shared.urlFound else { return }
                    
                    if let text = item as? String {
                        self.logger.info("Processing text for URL: \(text)")
                        
                        // Try to extract a URL from the text
                        if let extractedUrl = URLProcessor.extractURL(from: text) {
                            self.logger.info("URL found in text: \(extractedUrl)")
                            self.updateUI(with: extractedUrl)
                        } else {
                            self.logger.warning("No URL found in text")
                            
                            // Only show toast and dismiss if we haven't found a URL elsewhere
                            // and the URLProcessingManager confirms no URL was found
                            DispatchQueue.main.async {
                                if self.urlString.isEmpty && !URLProcessingManager.shared.urlFound {
                                    self.showToast(message: "No URL found in the shared text")
                                    
                                    // Dismiss after delay - only if we still don't have a URL and the manager confirms none found
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                                        if self.urlString.isEmpty && !URLProcessingManager.shared.urlFound {
                                            self.dismissShareSheet()
                                        }
                                    }
                                }
                            }
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
            logger.info("Found file URL")
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
        
        // Use URL extractor as fallback
        if let extractedUrl = URLProcessor.extractURL(from: html) {
            return URL(string: extractedUrl)
        }
        
        return nil
    }
    
    // MARK: - Image Processing

    /// Processes image attachments to look for QR codes
    /// - Parameter attachment: The NSItemProvider containing the image
    private func processImageAttachment(_ attachment: NSItemProvider) {
        // Check for image types
        let imageTypes = [kUTTypeImage as String, kUTTypeJPEG as String, kUTTypePNG as String, "public.image"]
        
        for imageType in imageTypes {
            if attachment.hasItemConformingToTypeIdentifier(imageType) {
                attachment.loadItem(forTypeIdentifier: imageType, options: nil) { [weak self] (item, error) in
                    DispatchQueue.main.async {
                        guard let self = self else { return }
                        
                        var image: UIImage? = nil
                        
                        // Handle different item types
                        if let imageURL = item as? URL {
                            self.logger.info("Processing image from URL: \(imageURL)")
                            image = UIImage(contentsOfFile: imageURL.path)
                        } else if let imageData = item as? Data {
                            self.logger.info("Processing image from data (\(imageData.count) bytes)")
                            image = UIImage(data: imageData)
                        } else if let actualImage = item as? UIImage {
                            self.logger.info("Processing UIImage directly")
                            image = actualImage
                        }
                        
                        // Process the image if we got one
                        if let validImage = image {
                            self.scanQRCodeFromImage(validImage)
                        } else {
                            self.logger.error("Failed to extract image from attachment")
                            self.showToast(message: "Could not process the image")
                            
                            // Dismiss after delay
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                                self.dismissShareSheet()
                            }
                        }
                    }
                }
                return
            }
        }
    }

    /// Scans an image for QR codes containing URLs
    /// - Parameter image: The UIImage to scan
    private func scanQRCodeFromImage(_ image: UIImage) {
        self.logger.info("Scanning image for QR codes")
        
        QRCodeScanner.scanQRCode(from: image) { [weak self] urlString in
            DispatchQueue.main.async {
                guard let self = self else { return }
                
                if let urlString = urlString {
                    self.logger.info("QR code URL found: \(urlString)")
                    self.updateUI(with: urlString)
                } else {
                    self.logger.warning("No URL found in QR code")
                    self.showToast(message: "No URL found in the QR code")
                    
                    // Dismiss after showing the toast
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                        self.dismissShareSheet()
                    }
                }
            }
        }
    }

    // MARK: - Toast Message

    /// Shows a toast message
    /// - Parameter message: The message to display
    private func showToast(message: String) {
        // First, make sure the view is loaded
        loadViewIfNeeded()
        
        let toastContainer = UIView()
        toastContainer.backgroundColor = UIColor.black.withAlphaComponent(0.7)
        toastContainer.layer.cornerRadius = 16
        toastContainer.clipsToBounds = true
        toastContainer.translatesAutoresizingMaskIntoConstraints = false
        
        let toastLabel = UILabel()
        toastLabel.textColor = UIColor.white
        toastLabel.textAlignment = .center
        toastLabel.font = UIFont.systemFont(ofSize: 14)
        toastLabel.text = message
        toastLabel.clipsToBounds = true
        toastLabel.numberOfLines = 0
        toastLabel.translatesAutoresizingMaskIntoConstraints = false
        
        toastContainer.addSubview(toastLabel)
        view.addSubview(toastContainer)
        
        NSLayoutConstraint.activate([
            toastLabel.leadingAnchor.constraint(equalTo: toastContainer.leadingAnchor, constant: 16),
            toastLabel.trailingAnchor.constraint(equalTo: toastContainer.trailingAnchor, constant: -16),
            toastLabel.topAnchor.constraint(equalTo: toastContainer.topAnchor, constant: 10),
            toastLabel.bottomAnchor.constraint(equalTo: toastContainer.bottomAnchor, constant: -10),
            
            toastContainer.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 20),
            toastContainer.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -20),
            toastContainer.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            toastContainer.bottomAnchor.constraint(equalTo: bottomSheetView.topAnchor, constant: -20)
        ])
        
        toastContainer.alpha = 0.0
        
        UIView.animate(withDuration: 0.2, delay: 0.0, options: .curveEaseIn, animations: {
            toastContainer.alpha = 1.0
        }, completion: { _ in
            UIView.animate(withDuration: 0.2, delay: 2.0, options: .curveEaseOut, animations: {
                toastContainer.alpha = 0.0
            }, completion: { _ in
                toastContainer.removeFromSuperview()
            })
        })
    }
    
    func updateUI(with urlString: String) {
        // Set the URL found flag first to prevent race conditions
        URLProcessingManager.shared.urlFound = true
        
        // Store original URL
        self.urlString = urlString
        
        // Use the Archive URL Service to process the URL
        self.processedUrlString = archiveService.processURL(urlString)
        
        // Show the processed URL as the primary URL in the UI
        if self.processedUrlString != urlString {
            // Show both URLs if they're different
            self.urlLabel.text = "Processed URL to archive:\n\(self.processedUrlString)\n\nOriginal: \(urlString)"
            self.logger.info("URL processed: \(urlString) -> \(self.processedUrlString)")
        } else {
            // Just show the URL if no changes were made
            self.urlLabel.text = self.processedUrlString
            self.logger.info("URL loaded (no processing needed): \(urlString)")
        }
        
        self.archiveButton.isEnabled = true
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
        if !processedUrlString.isEmpty {
            // Use archiveService to save the URL
            archiveService.saveURL(processedUrlString)
        logger.info("Saved processed URL to store: \(self.processedUrlString)")
        } else if !urlString.isEmpty {
            // Fallback in case something went wrong with processing
            logger.warning("Using original URL as fallback: \(self.urlString)")
            archiveService.saveURL(urlString)
        }

        // Always use the processed URL for redirecting to the app
        let finalUrl = !processedUrlString.isEmpty ? processedUrlString : urlString
        let encodedUrl = finalUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let appUrl = URL(string: "share2archivetoday://?url=\(encodedUrl)")!
        
        // Complete the extension request first
        self.extensionContext?.completeRequest(returningItems: nil) { _ in
            // Small delay is critical here
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                UIApplication.openAppWithURL(appUrl)
            }
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
        logger.info("Archive button tapped for URL: \(self.processedUrlString.isEmpty ? self.urlString : self.processedUrlString)")
        
        // Disable the button to prevent multiple taps
        archiveButton.isEnabled = false
        
        // Create a success message with a manual fallback
        let feedbackAlert = UIAlertController(
            title: "URL Archived",
            message: "The URL has been archived. You'll need to return to the Archive Today app to complete the process.",
            preferredStyle: .alert
        )
        
        // Add a button to try automatic opening
        feedbackAlert.addAction(UIAlertAction(title: "Open App", style: .default) { [weak self] _ in
            self?.saveAndRedirectToApp()
        })
        
        // Also provide a cancel option
        feedbackAlert.addAction(UIAlertAction(title: "Close", style: .cancel) { [weak self] _ in
            self?.dismissShareSheet()
        })
        
        present(feedbackAlert, animated: true)
    }
}

extension UIApplication {
    static func openAppWithURL(_ url: URL) {
        // Use this workaround to access UIApplication.shared from extension
        let selector = NSSelectorFromString("sharedApplication")
        guard let application = UIApplication.perform(selector)?.takeUnretainedValue() as? UIApplication else {
            return
        }
        
        if application.canOpenURL(url) {
            application.open(url, options: [:], completionHandler: nil)
        }
    }
}
