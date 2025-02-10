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

class ShareViewController: UIViewController {
    @IBOutlet weak var textView: UITextView!
    private let urlStore = URLStore.shared
    
    override func viewDidLoad() {
        super.viewDidLoad()
        textView?.placeholder = "Add a note (optional)"
    }

    @IBAction func cancelTapped(_ sender: Any) {
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
    
    @IBAction func doneTapped(_ sender: Any) {
        processSharedContent()
    }
    
    // MARK: - URL Processing Methods
    
    private func processArchiveUrl(_ url: String) -> String {
        guard let url = URL(string: url),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            return url
        }
        
        // Check if it's an archive.is URL
        if components.host?.contains("archive") == true,
           let path = components.path.split(separator: "/").last {
            return String(path)
        }
        
        return url.absoluteString
    }
    
    private func isTrackingParam(_ param: String) -> Bool {
        let trackingParams: Set<String> = [
            "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
            "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "tclid",
            "aff_id", "affiliate_id", "ref", "referer", "campaign_id", "ad_id",
            "adgroup_id", "adset_id", "creativetype", "placement", "network",
            "mc_eid", "mc_cid", "si", "icid", "_ga", "_gid", "scid", "click_id",
            "trk", "track", "trk_sid", "sid", "mibextid", "fb_action_ids",
            "fb_action_types", "twclid", "igshid", "s_kwcid", "sxsrf", "sca_esv",
            "source", "tbo", "sa", "ved", "pi"
        ]
        return trackingParams.contains(param)
    }
    
    private func isUnwantedYoutubeParam(_ param: String) -> Bool {
        return param == "feature"
    }
    
    private func cleanTrackingParamsFromUrl(_ urlString: String) -> String {
        guard let url = URL(string: urlString),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            return urlString
        }
        
        var newComponents = components
        
        // Handle YouTube URLs
        if components.host?.contains("youtube.com") == true || components.host?.contains("youtu.be") == true {
            // Remove 'music.' prefix from host
            newComponents.host = components.host?.replacingOccurrences(of: "music.", with: "")
            
            // Convert shorts to regular video format
            newComponents.path = components.path.replacingOccurrences(of: "/shorts/", with: "/v/")
        }
        
        // Handle Substack URLs
        if components.host?.hasSuffix(".substack.com") == true {
            var queryItems = components.queryItems ?? []
            queryItems.append(URLQueryItem(name: "no_cover", value: "true"))
            newComponents.queryItems = queryItems
        }
        
        // Filter out tracking parameters
        if let queryItems = components.queryItems {
            let filteredItems = queryItems.filter { item in
                !isTrackingParam(item.name) &&
                !(components.host?.contains("youtube.com") == true && isUnwantedYoutubeParam(item.name))
            }
            newComponents.queryItems = filteredItems.isEmpty ? nil : filteredItems
        }
        
        return newComponents.url?.absoluteString ?? urlString
    }
    
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
                        
                        // Save the URL before opening it
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
    
    private func completeRequest() {
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
}

// MARK: - UITextView Placeholder
extension UITextView {
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
    
    @objc private func handleTap() {
        if textColor == .placeholderText {
            text = ""
            textColor = .label
        }
        becomeFirstResponder()
    }
}
