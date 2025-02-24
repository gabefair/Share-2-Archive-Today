// Share-2-Archive-Today/URLShareExtension/ShareViewController.swift

import UIKit
import Social
import os.log

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
        getSharedUrl()
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
    
    func getSharedUrl() {
        guard let item = extensionContext?.inputItems.first as? NSExtensionItem,
              let itemProvider = item.attachments?.first else {
            handleError(message: "No URL found")
            return
        }
        
        logger.info("Processing share extension item")
        
        // Use String type identifier instead of UTType for better compatibility
        let urlTypeIdentifier = "public.url"
        
        itemProvider.loadItem(forTypeIdentifier: urlTypeIdentifier, options: nil) { [weak self] (item, error) in
            guard let self = self else { return }
            
            DispatchQueue.main.async {
                if let url = item as? URL {
                    self.urlString = url.absoluteString
                    self.urlLabel.text = url.absoluteString
                    self.archiveButton.isEnabled = true
                    self.logger.info("URL loaded: \(url.absoluteString)")
                } else if let error = error {
                    self.logger.error("Error loading URL: \(error.localizedDescription)")
                    self.handleError(message: error.localizedDescription)
                } else {
                    self.logger.error("Unknown error loading URL")
                    self.handleError(message: "Could not load URL")
                }
            }
        }
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
            logger.info("Pending archive flag set in UserDefaults")
        }
        
        // 3. Redirect to main app using custom URL scheme
        if let appUrl = URL(string: "share2archivetoday://") {
            logger.info("Redirecting to main app")
            
            // Complete the extension request and redirect to the main app
            self.extensionContext?.completeRequest(returningItems: nil) { [weak self] _ in
                guard let self = self else { return }
                
                // Use modern extensionContext API to open the URL
                self.extensionContext?.open(appUrl, completionHandler: { success in
                    self.logger.info("Open main app success: \(success)")
                })
            }
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
        
        // Show a success message
        let feedbackAlert = UIAlertController(
            title: "URL Archived",
            message: "The URL has been saved and will be archived when you return to the app.",
            preferredStyle: .alert
        )
        
        feedbackAlert.addAction(UIAlertAction(title: "OK", style: .default) { [weak self] _ in
            guard let self = self else { return }
            self.saveAndRedirectToApp()
        })
        
        present(feedbackAlert, animated: true)
    }
}
