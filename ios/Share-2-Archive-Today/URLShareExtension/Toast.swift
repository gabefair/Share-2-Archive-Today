import UIKit

/// A utility class for displaying toast messages
class Toast {
    
    /// Shows a toast message in the specified view
    /// - Parameters:
    ///   - message: The message to display
    ///   - view: The view to display the toast in
    ///   - duration: How long the toast should be displayed (in seconds)
    static func show(message: String, in view: UIView, duration: TimeInterval = 2.0) {
        let toastContainer = UIView()
        toastContainer.backgroundColor = UIColor.black.withAlphaComponent(0.7)
        toastContainer.layer.cornerRadius = 16
        toastContainer.clipsToBounds = true
        toastContainer.translatesAutoresizingMaskIntoConstraints = false
        
        let toastLabel = UILabel()
        toastLabel.textColor = UIColor.white
        toastLabel.textAlignment = .center
        toastLabel.font = UIFont.systemFont(ofSize: 14)
        toastLabel.text = message
        toastLabel.clipsToBounds = true
        toastLabel.numberOfLines = 0
        toastLabel.translatesAutoresizingMaskIntoConstraints = false
        
        toastContainer.addSubview(toastLabel)
        view.addSubview(toastContainer)
        
        NSLayoutConstraint.activate([
            toastLabel.leadingAnchor.constraint(equalTo: toastContainer.leadingAnchor, constant: 16),
            toastLabel.trailingAnchor.constraint(equalTo: toastContainer.trailingAnchor, constant: -16),
            toastLabel.topAnchor.constraint(equalTo: toastContainer.topAnchor, constant: 10),
            toastLabel.bottomAnchor.constraint(equalTo: toastContainer.bottomAnchor, constant: -10),
            
            toastContainer.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 20),
            toastContainer.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -20),
            toastContainer.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            toastContainer.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -32)
        ])
        
        toastContainer.alpha = 0.0
        
        UIView.animate(withDuration: 0.2, delay: 0.0, options: .curveEaseIn, animations: {
            toastContainer.alpha = 1.0
        }, completion: { _ in
            UIView.animate(withDuration: 0.2, delay: duration, options: .curveEaseOut, animations: {
                toastContainer.alpha = 0.0
            }, completion: { _ in
                toastContainer.removeFromSuperview()
            })
        })
    }
}
