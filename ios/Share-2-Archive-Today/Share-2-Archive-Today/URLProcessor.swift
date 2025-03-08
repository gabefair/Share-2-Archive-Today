import Foundation
import UIKit
import os.log

/// A utility class for processing and cleaning URLs before archiving
class URLProcessor {
    
    /// Logger for debugging
    private static let logger = Logger(subsystem: "org.Gnosco.Share-2-Archive-Today", category: "URLProcessor")
    
    /// Set of known tracking parameters that should be removed from URLs
    private static let trackingParams: Set<String> = [
        "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "tclid",
        "aff_id", "affiliate_id", "ref", "referer", "campaign_id", "ad_id",
        "adgroup_id", "adset_id", "creativetype", "placement", "network",
        "mc_eid", "mc_cid", "si", "icid", "_ga", "_gid", "scid", "click_id",
        "trk", "track", "trk_sid", "sid", "mibextid", "fb_action_ids",
        "fb_action_types", "twclid", "igshid", "s_kwcid", "sxsrf", "sca_esv",
        "source", "tbo", "sa", "ved", "pi"
    ]
    
    /// Set of YouTube-specific parameters that should be removed
    private static let unwantedYoutubeParams: Set<String> = [
        "feature"
    ]
    
    /// Processes a URL string before archiving
    /// - Parameter urlString: Original URL string to process
    /// - Returns: Processed URL string with tracking parameters removed and site-specific modifications applied
    static func processURL(_ urlString: String) -> String {
        // First process any archive.today URLs to extract the original URL
        let processedUrl = processArchiveURL(urlString)
        
        // Then clean tracking parameters and apply site-specific modifications
        return cleanTrackingParamsFromURL(processedUrl)
    }
    
    /// Extracts the original URL from an archive.today URL if applicable
    /// - Parameter urlString: The URL string which might be an archive URL
    /// - Returns: The original URL if it was an archive URL, otherwise the original string
    static func processArchiveURL(_ urlString: String) -> String {
        guard let url = URL(string: urlString) else {
            return urlString
        }
        
        // Check if this is an archive.today URL
        if let host = url.host,
           host.contains("archive.") {
            // Try to extract the original URL using regex
            let pattern = "archive\\.[a-z]+/o/[a-zA-Z0-9]+/(.+)"
            guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else {
                return urlString
            }
            
            let range = NSRange(urlString.startIndex..<urlString.endIndex, in: urlString)
            if let match = regex.firstMatch(in: urlString, options: [], range: range) {
                if let originalURLRange = Range(match.range(at: 1), in: urlString) {
                    return String(urlString[originalURLRange])
                }
            }
        }
        
        return urlString
    }
    
    /// Cleans tracking parameters from a URL
    /// - Parameter urlString: The URL string to clean
    /// - Returns: A cleaned URL string with tracking parameters removed and site-specific modifications applied
    static func cleanTrackingParamsFromURL(_ urlString: String) -> String {
        guard let url = URL(string: urlString),
              var components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            return urlString
        }
        
        // If there are no query parameters, return the original URL
        guard let queryItems = components.queryItems, !queryItems.isEmpty else {
            return urlString
        }
        
        var newQueryItems: [URLQueryItem] = []
        var isYouTube = false
        var isSubstack = false
        
        // Check for YouTube domain
        if let host = components.host {
            if host.contains("youtube.com") || host.contains("youtu.be") {
                isYouTube = true
                
                // Handle music.youtube.com -> youtube.com
                if host.hasPrefix("music.") {
                    components.host = host.replacingOccurrences(of: "music.", with: "")
                }
                
      
                
            } else if host.hasSuffix(".substack.com") {
                isSubstack = true
            }
        }
        
        // Handle nested query parameters (specifically for YouTube)
        if isYouTube {
            if components.path.contains("/shorts/") {
                components.path = components.path.replacingOccurrences(of: "/shorts/", with: "/v/")
            }
            
            if let qItem = queryItems.first(where: { $0.name == "q" }),
               let qValue = qItem.value,
               var nestedComponents = URLComponents(string: qValue) {
                
                // Filter out tracking parameters from nested URL
                if let nestedQueryItems = nestedComponents.queryItems {
                    let filteredNestedItems = nestedQueryItems.filter { item in
                        !trackingParams.contains(item.name)
                    }
                    nestedComponents.queryItems = filteredNestedItems.isEmpty ? nil : filteredNestedItems
                }
                
                // Add the cleaned nested URL back as a query parameter
                let nestedURLString = nestedComponents.string ?? qValue
                newQueryItems.append(URLQueryItem(name: "q", value: nestedURLString))
            }
        }
        
        // Add no_cover=true for Substack
        if isSubstack {
            newQueryItems.append(URLQueryItem(name: "no_cover", value: "true"))
        }
        
        // Filter regular query parameters
        for item in queryItems {
            // Skip the 'q' parameter if we've already handled it for YouTube
            if isYouTube && item.name == "q" {
                continue
            }
            
            // Skip tracking parameters
            if trackingParams.contains(item.name) {
                continue
            }
            
            // Skip unwanted YouTube parameters
            if isYouTube && unwantedYoutubeParams.contains(item.name) {
                continue
            }
            
            // Keep all other parameters
            newQueryItems.append(item)
        }
        
        // Set the filtered query items
        components.queryItems = newQueryItems.isEmpty ? nil : newQueryItems
        
        // Return the cleaned URL string
        return components.url?.absoluteString ?? urlString
    }
    
    /// Extracts a URL from a text string
    /// - Parameter text: The text string that might contain a URL
    /// - Returns: An extracted URL string or nil if none is found
    static func extractURL(from text: String) -> String? {
        // Try to find URLs with protocols using NSDataDetector
        let detector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)
        
        if let detector = detector {
            let matches = detector.matches(in: text, range: NSRange(text.startIndex..., in: text))
            
            if let match = matches.first, let range = Range(match.range, in: text) {
                let urlString = String(text[range])
                return cleanURL(urlString)
            }
        }
        
        // If no URL with protocol is found, look for potential bare domains
        let domainPattern = "(?:^|\\s+)([a-zA-Z0-9][a-zA-Z0-9-]*\\.[a-zA-Z0-9][a-zA-Z0-9-]*\\.[a-zA-Z]{2,}|[a-zA-Z0-9][a-zA-Z0-9-]*\\.[a-zA-Z]{2,})(?:/[^\\s]*)?(?:\\s+|$)"
        
        if let regex = try? NSRegularExpression(pattern: domainPattern, options: []) {
            let range = NSRange(text.startIndex..<text.endIndex, in: text)
            if let match = regex.firstMatch(in: text, options: [], range: range) {
                if let matchRange = Range(match.range(at: 1), in: text) {
                    let bareUrl = String(text[matchRange])
                    // Add https:// prefix and clean the URL
                    return cleanURL("https://\(bareUrl)")
                }
            }
        }
        
        return nil
    }
    
    /// Cleans a URL string by handling multiple protocols and removing trailing punctuation
    /// - Parameter url: The URL string to clean
    /// - Returns: A cleaned URL string
    static func cleanURL(_ url: String) -> String {
        var cleanedUrl = url
        
        // Handle multiple protocols by taking the last valid one
        if let lastHttpsIndex = url.lastIndex(of: "https://".last!), url[url.startIndex..<lastHttpsIndex].contains("https://") {
            let index = url.index(lastHttpsIndex, offsetBy: -7) // -7 for "https://"
            cleanedUrl = String(url[index...])
        } else if let lastHttpIndex = url.lastIndex(of: "http://".last!), url[url.startIndex..<lastHttpIndex].contains("http://") {
            let index = url.index(lastHttpIndex, offsetBy: -6) // -6 for "http://"
            cleanedUrl = String(url[index...])
        }
        
        // Add protocol if missing
        if !cleanedUrl.hasPrefix("http://") && !cleanedUrl.hasPrefix("https://") {
            cleanedUrl = "https://\(cleanedUrl)"
        }
        
        // Remove any trailing punctuation
        while !cleanedUrl.isEmpty && ".,:;)".contains(cleanedUrl.last!) {
            cleanedUrl.removeLast()
        }
        
        return cleanedUrl
    }
}
