//
//  URLHandlerService.swift
//  Share 2 Archive Today
//
//  Created by Gabirel Fair on 2/8/25.
//


// URLHandlerService.swift
import Foundation
import UIKit

class URLHandlerService {
    public static let shared = URLHandlerService()
    private init() {}
    
    private let appGroupID = "group.org.Gnosco.Share-2-Archive-Today"
    
    public var userDefaults: UserDefaults? {
        return UserDefaults(suiteName: appGroupID)
    }
    
    // Save URL to shared UserDefaults
    func saveURL(_ url: String) {
        guard let userDefaults = userDefaults else { return }
        var savedURLs = userDefaults.stringArray(forKey: "SavedURLs") ?? []
        savedURLs.append(url)
        userDefaults.set(savedURLs, forKey: "SavedURLs")
    }
    
    // Show toast-style message using UIWindow
    func showToast(message: String, in viewController: UIViewController) {
        let toastLabel = UILabel()
        toastLabel.backgroundColor = UIColor.black.withAlphaComponent(0.6)
        toastLabel.textColor = .white
        toastLabel.textAlignment = .center
        toastLabel.font = .systemFont(ofSize: 14)
        toastLabel.text = message
        toastLabel.alpha = 0
        toastLabel.layer.cornerRadius = 10
        toastLabel.clipsToBounds = true
        toastLabel.numberOfLines = 0
        
        let maxSize = CGSize(width: 250, height: 1000)
        var labelSize = toastLabel.sizeThatFits(maxSize)
        labelSize.width += 20
        labelSize.height += 20
        toastLabel.frame = CGRect(x: viewController.view.frame.width/2 - labelSize.width/2,
                                y: viewController.view.frame.height - 100,
                                width: labelSize.width,
                                height: labelSize.height)
        
        viewController.view.addSubview(toastLabel)
        
        UIView.animate(withDuration: 0.3, delay: 0, options: .curveEaseIn, animations: {
            toastLabel.alpha = 1
        }, completion: { _ in
            UIView.animate(withDuration: 0.3, delay: 1.5, options: .curveEaseOut, animations: {
                toastLabel.alpha = 0
            }, completion: { _ in
                toastLabel.removeFromSuperview()
            })
        })
    }
}
