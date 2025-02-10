//
//  URLStore.swift
//  Share-2-Archive-Today
//
//  Created by Gabirel Fair on 2/9/25.
//


import Foundation

class URLStore {
    static let shared = URLStore()
    private let defaults: UserDefaults
    
    private let urlsKey = "saved_urls"
    
    private init() {
        // Initialize with the specified app group identifier
        guard let defaults = UserDefaults(suiteName: "group.org.Gnosco.Share-2-Archive-Today") else {
            fatalError("Failed to initialize UserDefaults with app group")
        }
        self.defaults = defaults
    }
    
    func saveURL(_ urlString: String) {
        var urls = getSavedURLs()
        // Only add if not already present
        if !urls.contains(urlString) {
            urls.append(urlString)
            defaults.set(urls, forKey: urlsKey)
        }
    }
    
    func getSavedURLs() -> [String] {
        return defaults.stringArray(forKey: urlsKey) ?? []
    }
    
    func removeURL(at index: Int) {
        var urls = getSavedURLs()
        guard index < urls.count else { return }
        urls.remove(at: index)
        defaults.set(urls, forKey: urlsKey)
    }
}
