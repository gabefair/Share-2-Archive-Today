//
//  ShareViewController.swift
//  URLShareExtension
//
//  Created by Gabirel Fair on 2/8/25.
//

import UIKit
import UniformTypeIdentifiers
import MobileCoreServices
import Vision

class ShareViewController: UIViewController {
    private let urlHandler = URLHandlerService.shared
    private let appGroupID = "group.org.Gnosco.Share-2-Archive-Today"
    
    override func viewDidLoad() {
        super.viewDidLoad()
        handleShareExtension()
    }
    
    private func handleShareExtension() {
        let extensionItem = extensionContext?.inputItems.first as? NSExtensionItem
        guard let itemProvider = extensionItem?.attachments?.first else {
            completeRequest()
            return
        }
        
        if itemProvider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
            handleUrlShare(itemProvider)
        } else if itemProvider.hasItemConformingToTypeIdentifier(UTType.text.identifier) {
            handleTextShare(itemProvider)
        } else if itemProvider.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
            handleImageShare(itemProvider)
        } else {
            completeRequest()
        }
    }
    
    private func handleUrlShare(_ itemProvider: NSItemProvider) {
        itemProvider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] (item, error) in
            guard let url = item as? URL else {
                self?.completeRequest()
                return
            }
            
            DispatchQueue.main.async {
                let processedUrl = self?.processArchiveUrl(url.absoluteString) ?? url.absoluteString
                let cleanedUrl = self?.cleanTrackingParamsFromUrl(processedUrl) ?? processedUrl
                self?.openInBrowser(cleanedUrl)
            }
        }
    }
    
    private func handleTextShare(_ itemProvider: NSItemProvider) {
        itemProvider.loadItem(forTypeIdentifier: UTType.text.identifier, options: nil) { [weak self] (item, error) in
            guard let text = item as? String else {
                self?.completeRequest()
                return
            }
            
            DispatchQueue.main.async {
                if let url = self?.extractUrl(from: text) {
                    let processedUrl = self?.processArchiveUrl(url) ?? url
                    let cleanedUrl = self?.cleanTrackingParamsFromUrl(processedUrl) ?? processedUrl
                    self?.openInBrowser(cleanedUrl)
                } else {
                    self?.completeRequest()
                }
            }
        }
    }
    
    private func handleImageShare(_ itemProvider: NSItemProvider) {
        itemProvider.loadItem(forTypeIdentifier: UTType.image.identifier, options: nil) { [weak self] (item, error) in
            guard let url = item as? URL else {
                self?.completeRequest()
                return
            }
            
            guard let data = try? Data(contentsOf: url),
                  let image = UIImage(data: data) else {
                self?.completeRequest()
                return
            }
            
            DispatchQueue.main.async {
                self?.extractQRCode(from: image) { qrUrl in
                    if let qrUrl = qrUrl {
                        let processedUrl = self?.processArchiveUrl(qrUrl) ?? qrUrl
                        let cleanedUrl = self?.cleanTrackingParamsFromUrl(processedUrl) ?? processedUrl
                        self?.openInBrowser(cleanedUrl)
                    } else {
                        self?.completeRequest()
                    }
                }
            }
        }
    }
    
    private func extractQRCode(from image: UIImage, completion: @escaping (String?) -> Void) {
        guard let cgImage = image.cgImage else {
            urlHandler.showToast(message: "Could not process image", in: self)
            completion(nil)
            return
        }
        
        let request = VNDetectBarcodesRequest { request, error in
            if let error = error {
                DispatchQueue.main.async {
                    self.urlHandler.showToast(message: "Error scanning QR code: \(error.localizedDescription)", in: self)
                }
                completion(nil)
                return
            }
            
            guard let results = request.results as? [VNBarcodeObservation],
                  let qrCode = results.first(where: { $0.symbology == .qr }),
                  let urlString = qrCode.payloadStringValue else {
                DispatchQueue.main.async {
                    self.urlHandler.showToast(message: "No QR code found in image", in: self)
                }
                completion(nil)
                return
            }
            
            DispatchQueue.main.async {
                self.urlHandler.showToast(message: "URL found in QR code", in: self)
            }
            completion(urlString)
        }
        
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        try? handler.perform([request])
    }
    
    private func extractUrl(from text: String) -> String? {
        // URL with protocol
        let urlRegex = try? NSRegularExpression(pattern: "https?://(?:www\\.)?[\\w\\d\\-_]+(?:\\.[\\w\\d\\-_]+)+[\\w\\d\\-.,@?^=%&:/~+#]*", options: .caseInsensitive)
        if let match = urlRegex?.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) {
            return (text as NSString).substring(with: match.range)
        }
        
        // Bare domain
        let domainRegex = try? NSRegularExpression(pattern: "(?:^|\\s+)((?:[a-zA-Z0-9][a-zA-Z0-9-]*\\.)+?[a-zA-Z]{2,}(?:/[^\\s]*)?)(?:\\s+|$)", options: .caseInsensitive)
        if let match = domainRegex?.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) {
            let domain = (text as NSString).substring(with: match.range(at: 1))
            return "https://" + domain.trimmingCharacters(in: .whitespaces)
        }
        
        return nil
    }
    
    private func processArchiveUrl(_ url: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: "archive\\.[a-z]+/o/[a-zA-Z0-9]+/(.+)", options: []),
              let match = regex.firstMatch(in: url, range: NSRange(url.startIndex..., in: url)) else {
            return url
        }
        
        return (url as NSString).substring(with: match.range(at: 1))
    }
    
    private func cleanTrackingParamsFromUrl(_ urlString: String) -> String {
        guard let url = URL(string: urlString),
              var components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            return urlString
        }
        
        let trackingParams = Set([
            "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
            "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "tclid",
            "aff_id", "affiliate_id", "ref", "referer", "campaign_id", "ad_id",
            "adgroup_id", "adset_id", "creativetype", "placement", "network",
            "mc_eid", "mc_cid", "si", "icid", "_ga", "_gid", "scid", "click_id",
            "trk", "track", "trk_sid", "sid", "mibextid", "fb_action_ids",
            "fb_action_types", "twclid", "igshid", "s_kwcid", "sxsrf", "sca_esv",
            "source", "tbo", "sa", "ved", "pi"
        ])
        
        let youtubeParams = Set(["feature"])
        
        if components.host?.contains("youtube.com") == true || components.host?.contains("youtu.be") == true {
            // Handle YouTube specific cleaning
            components.host = components.host?.replacingOccurrences(of: "music.", with: "")
            components.path = components.path.replacingOccurrences(of: "/shorts/", with: "/v/")
            
            if let queryItems = components.queryItems {
                components.queryItems = queryItems.filter { item in
                    !trackingParams.contains(item.name) && !youtubeParams.contains(item.name)
                }
            }
        } else if components.host?.hasSuffix(".substack.com") == true {
            // Handle Substack
            var queryItems = components.queryItems ?? []
            queryItems.append(URLQueryItem(name: "no_cover", value: "true"))
            components.queryItems = queryItems
        } else {
            // Regular URL cleaning
            if let queryItems = components.queryItems {
                components.queryItems = queryItems.filter { !trackingParams.contains($0.name) }
            }
        }
        
        return components.url?.absoluteString ?? urlString
    }
    
    private func openInBrowser(_ url: String) {
        guard let encodedUrl = url.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let archiveUrl = URL(string: "https://archive.today/?run=1&url=\(encodedUrl)") else {
            completeRequest()
            return
        }
        
        // Save the URL to UserDefaults in the app group
        if let userDefaults = UserDefaults(suiteName: appGroupID) {
            userDefaults.set(archiveUrl.absoluteString, forKey: "pendingArchiveURL")
            userDefaults.synchronize()
        }
        
        completeRequest()
    }
    
    private func completeRequest() {
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
}
