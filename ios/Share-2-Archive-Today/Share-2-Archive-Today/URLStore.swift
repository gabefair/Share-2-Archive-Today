//
//  URLStore.swift
//  Share-2-Archive-Today
//
//  Created by Gabirel Fair on 2/9/25.
//


import Foundation

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
    private let appGroupIdentifier = "group.your.app.identifier" // Update this with your app group identifier
    
    /// Private initializer for singleton pattern
    private init() {
        defaults = UserDefaults(suiteName: appGroupIdentifier)
    }
    
    /// Saves a URL to persistent storage
    /// - Parameter urlString: The URL string to save
    /// - Returns: Boolean indicating success of the save operation
    @discardableResult
    func saveURL(_ urlString: String) -> Bool {
        guard let defaults = defaults else { return false }
        
        var savedURLs = getSavedURLs()
        
        // Avoid duplicates
        if !savedURLs.contains(urlString) {
            savedURLs.append(urlString)
            defaults.set(savedURLs, forKey: urlStorageKey)
            defaults.synchronize()
            return true
        }
        
        return false
    }
    
    /// Retrieves all saved URLs from persistent storage
    /// - Returns: Array of saved URL strings
    func getSavedURLs() -> [String] {
        guard let defaults = defaults else { return [] }
        return defaults.stringArray(forKey: urlStorageKey) ?? []
    }
    
    /// Removes a URL from persistent storage
    /// - Parameter urlString: The URL string to remove
    /// - Returns: Boolean indicating success of the removal operation
    @discardableResult
    func removeURL(_ urlString: String) -> Bool {
        guard let defaults = defaults else { return false }
        
        var savedURLs = getSavedURLs()
        if let index = savedURLs.firstIndex(of: urlString) {
            savedURLs.remove(at: index)
            defaults.set(savedURLs, forKey: urlStorageKey)
            defaults.synchronize()
            return true
        }
        
        return false
    }
    
    /// Removes all saved URLs from persistent storage
    func clearAllURLs() {
        defaults?.removeObject(forKey: urlStorageKey)
        defaults?.synchronize()
    }
    
    /// Checks if a URL exists in persistent storage
    /// - Parameter urlString: The URL string to check
    /// - Returns: Boolean indicating if the URL exists
    func urlExists(_ urlString: String) -> Bool {
        return getSavedURLs().contains(urlString)
    }
}
