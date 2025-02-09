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

class ShareViewController: SLComposeServiceViewController {
    
    override func isContentValid() -> Bool {
        // We'll handle the validation in didSelectPost
        return true
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        placeholder = "Add a note (optional)"
    }

    override func didSelectPost() {
        // Create a dispatch group to ensure we handle all items before completing
        let group = DispatchGroup()
        
        // Get all attachment items from the extension context
        guard let extensionItem = extensionContext?.inputItems.first as? NSExtensionItem,
              let attachments = extensionItem.attachments else {
            completeRequest()
            return
        }
        
        // Track if we found any valid URLs
        var foundURL = false
        
        // Iterate through all attachments
        for itemProvider in attachments {
            // Check for URL type identifier
            if itemProvider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                group.enter()
                
                // Explicitly specify NSURL as the expected class
                itemProvider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { (item, error) in
                    defer { group.leave() }
                    
                    // Handle different types of URL items
                    if let url = item as? URL {
                        foundURL = true
                        URLStore.shared.saveURL(url.absoluteString)
                    } else if let urlString = item as? String, let url = URL(string: urlString) {
                        foundURL = true
                        URLStore.shared.saveURL(url.absoluteString)
                    }
                }
            }
        }
        
        // Wait for all items to be processed
        group.notify(queue: .main) { [weak self] in
            if !foundURL {
                // Show an error if no valid URLs were found
                let alert = UIAlertController(
                    title: "No URL Found",
                    message: "No valid URL was found in the shared content.",
                    preferredStyle: .alert
                )
                alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in
                    self?.completeRequest()
                })
                self?.present(alert, animated: true)
            } else {
                self?.completeRequest()
            }
        }
    }
    
    private func completeRequest() {
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }

    override func configurationItems() -> [Any]! {
        return []
    }
}
