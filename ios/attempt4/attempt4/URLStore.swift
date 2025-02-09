//
//  URLStore.swift
//  attempt4
//
//  Created by Gabirel Fair on 2/9/25.
//


import Foundation

// This class handles storing and retrieving URLs using UserDefaults in the shared app group
class URLStore {
    static let shared = URLStore()
    private let defaults: UserDefaults
    
    private let urlsKey = "saved_urls"
    
    private init() {
        // Initialize with the shared app group identifier
        guard let defaults = UserDefaults(suiteName: "group.org.Gnosco.attempt4") else {
            fatalError("Failed to initialize UserDefaults with app group")
        }
        self.defaults = defaults
    }
    
    func saveURL(_ urlString: String) {
        var urls = getSavedURLs()
        urls.append(urlString)
        defaults.set(urls, forKey: urlsKey)
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
