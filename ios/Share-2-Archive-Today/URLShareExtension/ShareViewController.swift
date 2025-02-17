// Share-2-Archive-Today/URLShareExtension/ShareViewController.swift

import UIKit
import Social
import UniformTypeIdentifiers

/// A view controller that handles the share extension functionality for archiving URLs
/// This implementation follows a bottom sheet presentation style with proper error handling
@objc(ShareViewController)
class ShareViewController: UIViewController {
    
    // MARK: - Properties
    
    /// The URL string to be archived
    private var urlString: String = ""
    
    /// Height of the title section
    private let titleHeight: CGFloat = 64
    
    /// URL scheme for the main app
    private let appURL = "share2archivetoday://"
    
    /// Indicates if the share extension can be used (based on authentication)
    private var isAuthenticated = false
    
    // MARK: - UI Components
    
    private lazy var titleLabel: UILabel = {
        let label = UILabel()
        label.font = .systemFont(ofSize: 18, weight: .bold)
        label.textColor = .label
        label.text = "Archive this URL?"
        return label
    }()
    
    private lazy var closeButton: UIButton = {
        let button = UIButton(type: .system)
        button.setImage(UIImage(systemName: "xmark.circle.fill"), for: .normal)
        button.tintColor = .label
        return button
    }()
    
    private lazy var bottomSheetView: UIView = {
        let view = UIView()
        view.backgroundColor = .systemBackground
        view.layer.cornerRadius = 20
        view.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        view.clipsToBounds = true
        return view
    }()
    
    private lazy var urlPreviewLabel: UILabel = {
        let label = UILabel()
        label.font = .systemFont(ofSize: 14)
        label.textColor = .secondaryLabel
        label.numberOfLines = 2
        return label
    }()
    
    private lazy var archiveButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("Archive URL", for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.backgroundColor = .systemBlue
        button.layer.cornerRadius = 12
        button.isEnabled = false
        return button
    }()
    
    // MARK: - Lifecycle Methods
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        setupUI()
        setupLayout()
        setupActions()
        getSharedUrl()
        checkAuthenticationStatus()
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        
        animateBottomSheet()
    }
}

// MARK: - Private Methods

private extension ShareViewController {
    
    func setupUI() {
        view.backgroundColor = .clear
        
        view.addSubview(bottomSheetView)
        bottomSheetView.addSubview(titleLabel)
        bottomSheetView.addSubview(closeButton)
        bottomSheetView.addSubview(urlPreviewLabel)
        bottomSheetView.addSubview(archiveButton)
    }
    
    func setupLayout() {
        // Using manual frame-based layout for simplicity
        // In production, use AutoLayout constraints
        
        let padding: CGFloat = 20
        let buttonHeight: CGFloat = 52
        
        bottomSheetView.frame = CGRect(
            x: 0,
            y: view.bounds.height,
            width: view.bounds.width,
            height: 250
        )
        
        titleLabel.frame = CGRect(
            x: padding,
            y: padding,
            width: 200,
            height: 30
        )
        
        closeButton.frame = CGRect(
            x: bottomSheetView.bounds.width - 50,
            y: padding,
            width: 30,
            height: 30
        )
        
        urlPreviewLabel.frame = CGRect(
            x: padding,
            y: titleLabel.frame.maxY + padding,
            width: bottomSheetView.bounds.width - (padding * 2),
            height: 40
        )
        
        archiveButton.frame = CGRect(
            x: padding,
            y: bottomSheetView.bounds.height - buttonHeight - padding,
            width: bottomSheetView.bounds.width - (padding * 2),
            height: buttonHeight
        )
    }
    
    func setupActions() {
        closeButton.addTarget(self, action: #selector(closeButtonTapped), for: .touchUpInside)
        archiveButton.addTarget(self, action: #selector(archiveButtonTapped), for: .touchUpInside)
    }
    
    func animateBottomSheet() {
        UIView.animate(withDuration: 0.3, delay: 0, options: .curveEaseOut) {
            self.bottomSheetView.frame.origin.y = self.view.bounds.height - self.bottomSheetView.bounds.height
        }
    }
    
    func getSharedUrl() {
        guard let item = extensionContext?.inputItems.first as? NSExtensionItem,
              let itemProvider = item.attachments?.first else {
            handleError(message: "No URL found")
            return
        }
        
        itemProvider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] (item, error) in
            guard let self = self else { return }
            
            DispatchQueue.main.async {
                if let url = item as? URL {
                    self.urlString = url.absoluteString
                    self.urlPreviewLabel.text = url.absoluteString
                    self.archiveButton.isEnabled = true
                } else if let error = error {
                    self.handleError(message: error.localizedDescription)
                }
            }
        }
    }
    
    func checkAuthenticationStatus() {
        // In a real implementation, check if the user is authenticated
        // For now, we'll assume they are
        isAuthenticated = true
    }
    
    func handleError(message: String) {
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
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }
    
    func openMainApp() {
        guard let url = URL(string: appURL) else { return }
        
        extensionContext?.completeRequest(returningItems: nil) { [weak self] _ in
            _ = self?.openURL(url)
        }
    }
    
    @objc func openURL(_ url: URL) -> Bool {
        var responder: UIResponder? = self
        while responder != nil {
            if let application = responder as? UIApplication {
                return application.perform(#selector(openURL(_:)), with: url) != nil
            }
            responder = responder?.next
        }
        return false
    }
    
    // MARK: - Action Methods
    
    @objc func closeButtonTapped() {
        UIView.animate(withDuration: 0.3, animations: {
            self.bottomSheetView.frame.origin.y = self.view.bounds.height
        }) { _ in
            self.dismissShareSheet()
        }
    }
    
    @objc func archiveButtonTapped() {
        guard !urlString.isEmpty else { return }
        
        if !isAuthenticated {
            showAuthenticationAlert()
            return
        }
        
        // Create the archive.today URL
        guard let encodedUrl = urlString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let archiveUrl = URL(string: "https://archive.today/?run=1&url=\(encodedUrl)") else {
            handleError(message: "Could not create archive URL")
            return
        }
        
        // Pass the URL back to the main app
        let userActivity = NSUserActivity(activityType: "org.share2archivetoday.openURL")
        userActivity.webpageURL = archiveUrl
        
        let outputItem = NSExtensionItem()
        outputItem.userInfo = ["activity": userActivity]
        
        extensionContext?.completeRequest(returningItems: [outputItem], completionHandler: nil)
    }
    
    func showAuthenticationAlert() {
        let alert = UIAlertController(
            title: "Authentication Required",
            message: "Please log in to use this feature. Would you like to open the app?",
            preferredStyle: .alert
        )
        
        alert.addAction(UIAlertAction(title: "Open App", style: .default) { [weak self] _ in
            self?.openMainApp()
        })
        
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel) { [weak self] _ in
            self?.dismissShareSheet()
        })
        
        present(alert, animated: true)
    }
}
