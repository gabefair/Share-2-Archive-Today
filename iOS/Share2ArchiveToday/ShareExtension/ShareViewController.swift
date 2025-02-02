//ShareViewController.swift
import UIKit
import Social
import MobileCoreServices
import Vision
import UniformTypeIdentifiers
import Foundation

class ShareViewController: SLComposeServiceViewController {
    
    override func isContentValid() -> Bool {
        // We don't need to validate content since we process it immediately
        return true
    }
    
    override func didSelectPost() {
        // When the user selects our extension, process the shared content immediately
        if let item = extensionContext?.inputItems.first as? NSExtensionItem {
            handleShare(item)
        } else {
            self.extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
        }
    }
    
    private func handleShare(_ item: NSExtensionItem) {
        // Handle text shares
        if let textProvider = item.attachments?.first(where: {
            $0.hasItemConformingToTypeIdentifier(UTType.text.identifier)
        }) {
            handleTextShare(textProvider)
            return
        }
        
        // Handle image shares
        if let imageProvider = item.attachments?.first(where: {
            $0.hasItemConformingToTypeIdentifier(UTType.image.identifier)
        }) {
            handleImageShare(imageProvider)
            return
        }
        
        // Handle URL shares
        if let urlProvider = item.attachments?.first(where: {
            $0.hasItemConformingToTypeIdentifier(UTType.url.identifier)
        }) {
            handleUrlShare(urlProvider)
            return
        }
        
        showError("Unsupported content type")
    }
    
    private func handleTextShare(_ provider: NSItemProvider) {
        provider.loadItem(forTypeIdentifier: UTType.text.identifier, options: nil) { (text, error) in
            if let error = error {
                self.showError("Error processing text: \(error.localizedDescription)")
                return
            }
            
            guard let sharedText = text as? String else {
                self.showError("Invalid text format")
                return
            }
            
            if let url = self.extractUrl(from: sharedText) {
                let processedUrl = self.processArchiveUrl(url)
                let cleanedUrl = self.cleanTrackingParams(from: processedUrl)
                self.openInArchive(url: cleanedUrl)
            } else {
                self.showError("No URL found in shared text")
            }
        }
    }
    
    private func handleImageShare(_ provider: NSItemProvider) {
        provider.loadItem(forTypeIdentifier: UTType.image.identifier, options: nil) { (imageData, error) in
            if let error = error {
                self.showError("Error processing image: \(error.localizedDescription)")
                return
            }
            
            guard let image = self.loadImage(from: imageData) else {
                self.showError("Invalid image format")
                return
            }
            
            self.extractQRCode(from: image) { result in
                switch result {
                case .success(let url):
                    let cleanedUrl = self.cleanTrackingParams(from: url)
                    self.openInArchive(url: cleanedUrl)
                case .failure(let error):
                    self.showError(error.localizedDescription)
                }
            }
        }
    }
    
    private func processArchiveUrl(_ url: String) -> String {
        let pattern = "archive\\.[a-z]+/o/[a-zA-Z0-9]+/(.+)"
        if let regex = try? NSRegularExpression(pattern: pattern),
           let match = regex.firstMatch(in: url, range: NSRange(url.startIndex..., in: url)),
           let range = Range(match.range(at: 1), in: url) {
            return String(url[range])
        }
        return url
    }
    
    private func handleUrlShare(_ provider: NSItemProvider) {
        provider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { (url, error) in
            if let error = error {
                self.showError("Error processing URL: \(error.localizedDescription)")
                return
            }
            
            guard let shareURL = url as? URL else {
                self.showError("Invalid URL format")
                return
            }
            
            let processedUrl = self.processArchiveUrl(shareURL.absoluteString)
            let cleanedUrl = self.cleanTrackingParams(from: processedUrl)
            self.openInArchive(url: cleanedUrl)
        }
    }
    
    private func loadImage(from data: Any?) -> UIImage? {
        if let data = data as? Data {
            return UIImage(data: data)
        }
        if let url = data as? URL {
            return UIImage(contentsOfFile: url.path)
        }
        return nil
    }
    
    private func extractQRCode(from image: UIImage, completion: @escaping (Result<String, Error>) -> Void) {
        guard let cgImage = image.cgImage else {
            completion(.failure(NSError(domain: "", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid image format"])))
            return
        }
        
        let request = VNDetectBarcodesRequest { request, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let results = request.results as? [VNBarcodeObservation],
                  let qrCode = results.first(where: { $0.symbology == .qr }),
                  let urlString = qrCode.payloadStringValue else {
                completion(.failure(NSError(domain: "", code: -1, userInfo: [NSLocalizedDescriptionKey: "No QR code found"])))
                return
            }
            
            completion(.success(urlString))
        }
        
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        try? handler.perform([request])
    }
    
    private func extractUrl(from text: String) -> String? {
        // URL with protocol regex
        let protocolPattern = "https?://(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&/=]*)"
        
        // Bare domain regex
        let domainPattern = "(?:^|\\s+)((?:[a-zA-Z0-9][a-zA-Z0-9-]*\\.)+?[a-zA-Z]{2,}(?:/[^\\s]*)?)"
        
        if let url = text.range(of: protocolPattern, options: .regularExpression)
            .map({ String(text[$0]) }) {
            return cleanUrl(url)
        }
        
        if let domain = text.range(of: domainPattern, options: .regularExpression)
            .map({ String(text[$0]) }) {
            return cleanUrl("https://\(domain.trimmingCharacters(in: .whitespaces))")
        }
        
        return nil
    }
    
    private func cleanUrl(_ url: String) -> String {
        var cleaned = url.trimmingCharacters(in: .whitespaces)
        
        // Remove trailing punctuation
        while cleaned.last?.isWhitespace == true ||
              cleaned.last == "." ||
              cleaned.last == "," ||
              cleaned.last == ";" ||
              cleaned.last == ")" {
            cleaned.removeLast()
        }
        
        return cleaned
    }
    
    private func cleanTrackingParams(from url: String) -> String {
        guard var components = URLComponents(string: url) else { return url }
        
        // Special handling for YouTube URLs
        if components.host?.contains("youtube.com") == true ||
           components.host?.contains("youtu.be") == true {
            components.host = components.host?.replacingOccurrences(of: "music.", with: "")
            components.path = components.path.replacingOccurrences(of: "/shorts/", with: "/v/")
        }
        
        // Special handling for Substack
        if components.host?.hasSuffix(".substack.com") == true {
            var queryItems = components.queryItems ?? []
            queryItems.append(URLQueryItem(name: "no_cover", value: "true"))
            components.queryItems = queryItems
        }
        
        // Remove tracking parameters
        let trackingParams = Set([
            "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
            "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "tclid",
            "aff_id", "affiliate_id", "ref", "referer", "campaign_id", "ad_id",
            "adgroup_id", "adset_id", "creativetype", "placement", "network",
            "mc_eid", "mc_cid", "si", "icid", "_ga", "_gid", "scid", "click_id",
            "trk", "track", "trk_sid", "sid", "mibextid", "fb_action_ids",
            "fb_action_types", "twclid", "igshid", "s_kwcid", "sxsrf", "sca_esv",
            "source", "tbo", "sa", "ved", "pi", "feature"
        ])
        
        components.queryItems = components.queryItems?.filter { item in
            !trackingParams.contains(item.name)
        }
        
        return components.url?.absoluteString ?? url
    }
    
    private func openInArchive(url: String) {
        guard let encodedUrl = url.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let archiveUrl = URL(string: "https://archive.today/?run=1&url=\(encodedUrl)") else {
            showError("Invalid URL format")
            return
        }
        
        openURL(archiveUrl)
    }
    
    private func openURL(_ url: URL) {
        let urlToOpen = URL(string: "safari-" + url.absoluteString) ?? url
        
        extensionContext?.open(urlToOpen, completionHandler: { success in
            // After opening the URL, close the share extension
            self.extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
        })
        
       
    }
    
    private func showError(_ message: String) {
        DispatchQueue.main.async {
            let alert = UIAlertController(title: "Error",
                                        message: message,
                                        preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in
                self.extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
            })
            self.present(alert, animated: true)
        }
    }
    
    override func configurationItems() -> [Any]! {
        return []
    }
}
