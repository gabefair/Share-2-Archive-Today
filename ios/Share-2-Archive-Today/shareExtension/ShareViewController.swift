//
//  ShareViewController.swift
//  shareExtension

import UIKit

/// A view controller for the Share Extension that processes a shared URL and opens it in the default browser.
///
/// This controller is instantiated from the Storyboard and automatically processes input items on load.
class ShareViewController: UIViewController {

    /// Called after the controller’s view is loaded into memory.
    ///
    /// This method triggers the processing of the extension’s input items.
    override func viewDidLoad() {
        super.viewDidLoad()
        processInputItems()
    }
    
    /**
     Processes the input items provided by the extension context.
     
     It iterates over the input items to locate the first item conforming to the "public.url" type.
     If found, it loads the URL and then calls `openURLInDefaultBrowser(url:)` on the main thread.
     If no URL is found or an error occurs during the loading process, the extension is completed.
     
     - Note: This method does not return a value.
     */
    private func processInputItems() {
        // Safely unwrap the input items as an array of NSExtensionItem.
        guard let extensionItems = extensionContext?.inputItems as? [NSExtensionItem] else {
            completeExtension()
            return
        }
        
        // Iterate through each extension item.
        for item in extensionItems {
            if let attachments = item.attachments {
                for provider in attachments {
                    // Check if the provider has a URL.
                    if provider.hasItemConformingToTypeIdentifier("public.url") {
                        provider.loadItem(forTypeIdentifier: "public.url", options: nil) { [weak self] (data, error) in
                            guard let self = self else { return }
                            
                            if let error = error {
                                // Log the error and complete the extension.
                                print("Error loading URL: \(error.localizedDescription)")
                                self.completeExtension()
                                return
                            }
                            
                            var url: URL?
                            // The data might be provided as a URL or a String.
                            if let urlData = data as? URL {
                                url = urlData
                            } else if let urlString = data as? String {
                                url = URL(string: urlString)
                            }
                            
                            // If a valid URL is retrieved, open it.
                            guard let validURL = url else {
                                self.completeExtension()
                                return
                            }
                            
                            // Open the URL on the main thread.
                            DispatchQueue.main.async {
                                self.openURLInDefaultBrowser(url: validURL)
                            }
                        }
                        // Process only the first URL.
                        return
                    }
                }
            }
        }
        // If no URL is found, complete the extension.
        completeExtension()
    }
    
    /**
     Opens the specified URL in the user's default browser.
     
     This method uses the extension context's `open(_:completionHandler:)` to request that the system open the URL externally.
     
     - Parameter url: The URL to be opened.
     
     - Note: There is no return value. In case of failure to open the URL, an error message is logged.
     */
    private func openURLInDefaultBrowser(url: URL) {
        extensionContext?.open(url, completionHandler: { [weak self] success in
            if !success {
                print("Failed to open URL: \(url.absoluteString)")
            }
            self?.completeExtension()
        })
    }
    
    /**
     Completes the share extension request.
     
     This method dismisses the share extension's UI by informing the extension context that the task is complete.
     
     - Note: There is no return value.
     */
    private func completeExtension() {
        extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }
}

