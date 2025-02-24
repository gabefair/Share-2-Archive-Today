//
//  URLStore.swift
//  Share-2-Archive-Today
//
//  Created by Gabirel Fair on 2/9/25.
//
import Foundation
import os.log

/// A singleton class that manages the storage and retrieval of URLs using UserDefaults
/// This class provides thread-safe access to a shared URL storage that persists across app launches
/// Manages the storage and retrieval of archived URLs using UserDefaults with App Groups
class URLStore {
    /// Shared instance for singleton access
    static let shared = URLStore()
    
    /// UserDefaults suite for sharing data between app and extension
    private let defaults: UserDefaults?
    
    /// Key for storing URLs in UserDefaults
    private let urlStorageKey = "saved_urls"
    
    /// Group identifier for sharing data between app and extension
    private let appGroupIdentifier = "group.org.Gnosco.Share-2-Archive-Today"
    
    /// Logger for debugging
    private let logger = Logger(subsystem: "org.Gnosco.Share-2-Archive-Today", category: "URLStore")
    
    /// Private initializer for singleton pattern
    private init() {
        defaults = UserDefaults(suiteName: appGroupIdentifier)
        
        if defaults == nil {
            logger.error("Failed to initialize UserDefaults with app group: \(self.appGroupIdentifier)")
        } else {
            logger.info("URLStore initialized successfully with app group")
        }
    }
    
    /// Saves a URL to persistent storage
    /// - Parameter urlString: The URL string to save
    /// - Returns: Boolean indicating success of the save operation
    @discardableResult
    func saveURL(_ urlString: String) -> Bool {
        guard let defaults = defaults else {
            logger.error("Cannot save URL: UserDefaults is nil")
            return false
        }
        
        // Add basic URL validation
        guard !urlString.isEmpty, URL(string: urlString) != nil else {
            logger.warning("Attempted to save invalid URL: \(urlString)")
            return false
        }
        
        var savedURLs = getSavedURLs()
        
        // Avoid duplicates
        if !savedURLs.contains(urlString) {
            savedURLs.append(urlString)
            defaults.set(savedURLs, forKey: urlStorageKey)
            logger.info("Saved new URL to store: \(urlString)")
            return true
        } else {
            logger.info("URL already exists in store: \(urlString)")
        }
        
        return false
    }
    
    /// Retrieves all saved URLs from persistent storage
    /// - Returns: Array of saved URL strings
    func getSavedURLs() -> [String] {
        guard let defaults = defaults else {
            logger.error("Cannot get saved URLs: UserDefaults is nil")
            return []
        }
        
        let urls = defaults.stringArray(forKey: urlStorageKey) ?? []
        logger.debug("Retrieved \(urls.count) URLs from store")
        return urls
    }
    
    /// Removes a URL from persistent storage
    /// - Parameter urlString: The URL string to remove
    /// - Returns: Boolean indicating success of the removal operation
    @discardableResult
    func removeURL(_ urlString: String) -> Bool {
        guard let defaults = defaults else {
            logger.error("Cannot remove URL: UserDefaults is nil")
            return false
        }
        
        var savedURLs = getSavedURLs()
        if let index = savedURLs.firstIndex(of: urlString) {
            savedURLs.remove(at: index)
            defaults.set(savedURLs, forKey: urlStorageKey)
            logger.info("Removed URL from store: \(urlString)")
            return true
        } else {
            logger.warning("URL not found for removal: \(urlString)")
        }
        
        return false
    }
    
    /// Removes all saved URLs from persistent storage
    func clearAllURLs() {
        guard let defaults = defaults else {
            logger.error("Cannot clear URLs: UserDefaults is nil")
            return
        }
        
        defaults.removeObject(forKey: urlStorageKey)
        logger.info("Cleared all URLs from store")
    }
    
    /// Checks if a URL exists in persistent storage
    /// - Parameter urlString: The URL string to check
    /// - Returns: Boolean indicating if the URL exists
    func urlExists(_ urlString: String) -> Bool {
        let exists = getSavedURLs().contains(urlString)
        logger.debug("URL exists check: \(urlString) - \(exists)")
        return exists
    }
}
