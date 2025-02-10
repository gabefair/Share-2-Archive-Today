//
//  URLStore.swift
//  Share-2-Archive-Today
//
//  Created by Gabirel Fair on 2/9/25.
//


import Foundation

/// A singleton class that manages the storage and retrieval of URLs using UserDefaults
/// This class provides thread-safe access to a shared URL storage that persists across app launches
class URLStore {
    /// The shared instance of URLStore
    static let shared = URLStore()
    
    /// UserDefaults instance for storing URLs
    private let defaults: UserDefaults
    
    /// Key used to store URLs in UserDefaults
    private let urlsKey = "saved_urls"
    
    /// Private initializer to enforce singleton pattern
    /// - Throws: fatalError if UserDefaults cannot be initialized with the app group
    private init() {
        // Initialize with the specified app group identifier
        guard let defaults = UserDefaults(suiteName: "group.org.Gnosco.Share-2-Archive-Today") else {
            fatalError("Failed to initialize UserDefaults with app group")
        }
        self.defaults = defaults
    }
    
    /// Saves a URL to persistent storage
    /// - Parameter urlString: The URL string to save
    /// - Note: If the URL already exists in storage, it will not be added again
    func saveURL(_ urlString: String) {
        var urls = getSavedURLs()
        // Ensure the URL is not already stored
        if !urls.contains(where: { $0.caseInsensitiveCompare(urlString) == .orderedSame }) {
            urls.append(urlString)
            defaults.set(urls, forKey: urlsKey)
        }
    }
    
    /// Retrieves all saved URLs from storage
    /// - Returns: An array of URL strings, or an empty array if no URLs are saved
    func getSavedURLs() -> [String] {
        return defaults.stringArray(forKey: urlsKey) ?? []
    }
    
    /// Removes a URL from storage at the specified index
    /// - Parameter index: The index of the URL to remove
    /// - Note: If the index is out of bounds, this method does nothing
    func removeURL(at index: Int) {
        var urls = getSavedURLs()
        guard index < urls.count else { return }
        urls.remove(at: index)
        defaults.set(urls, forKey: urlsKey)
    }
}
