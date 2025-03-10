// URLProcessingManager.swift

import Foundation

/// Singleton to manage URL processing state across the share extension
class URLProcessingManager {
    /// Shared instance of the manager
    static let shared = URLProcessingManager()
    
    /// Flag indicating whether a URL has been found
    private var _urlFound = false
    
    /// Thread-safe access to urlFound flag
    var urlFound: Bool {
        get {
            // Using objc_sync_enter/exit for thread safety
            objc_sync_enter(self)
            defer { objc_sync_exit(self) }
            return _urlFound
        }
        set {
            objc_sync_enter(self)
            _urlFound = newValue
            objc_sync_exit(self)
        }
    }
    
    /// Reset the state of the manager
    func reset() {
        urlFound = false
    }
    
    /// Private initializer to enforce singleton pattern
    private init() {}
}
