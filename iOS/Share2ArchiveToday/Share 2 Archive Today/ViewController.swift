//
//  ViewController.swift
//  Share 2 Archive Today
//
//  Created by Gabirel Fair on 2/2/25.
//

import UIKit

class ViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        
        // Create and configure a label with instructions
        let label = UILabel()
        label.numberOfLines = 0
        label.textAlignment = .center
        label.text = "Share to Archive.today is ready!\n\nTo use, share any URL or text containing a URL from another app and select 'Share to Archive' from the share menu."
        
        // Set up constraints for the label
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            label.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            label.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20)
        ])
    }
}

