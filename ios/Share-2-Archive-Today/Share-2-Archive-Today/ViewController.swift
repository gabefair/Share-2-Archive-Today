//
//  ViewController.swift
//  Share-2-Archive-Today
//
//  Created by Gabirel Fair on 2/9/25.
//

import UIKit
import SafariServices

class ViewController: UIViewController {
    // Make sure this outlet exists and is properly connected
    @IBOutlet weak var tableView: UITableView!
    
    private var urls: [String] = []
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupTableView()
        navigationItem.title = "Saved URLs"
        
        // Add refresh control
        let refreshControl = UIRefreshControl()
        refreshControl.addTarget(self, action: #selector(refreshUrls), for: .valueChanged)
        tableView.refreshControl = refreshControl
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        refreshUrls()
    }
    
    private func setupTableView() {
        // Since we're using storyboard, we don't need to create the tableView programmatically
        // Just configure it
        tableView.delegate = self
        tableView.dataSource = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "URLCell")
    }
    
    @objc private func refreshUrls() {
        urls = URLStore.shared.getSavedURLs()
        tableView.reloadData()
        tableView.refreshControl?.endRefreshing()
    }
}

// MARK: - UITableViewDataSource
extension ViewController: UITableViewDataSource {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return urls.count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "URLCell", for: indexPath)
        cell.textLabel?.text = urls[indexPath.row]
        cell.accessoryType = .disclosureIndicator
        return cell
    }
    
    func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) {
        if editingStyle == .delete {
            URLStore.shared.removeURL(at: indexPath.row)
            refreshUrls()
        }
    }
}

// MARK: - UITableViewDelegate
extension ViewController: UITableViewDelegate {
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        
        guard let url = URL(string: urls[indexPath.row]) else { return }
        let safariVC = SFSafariViewController(url: url)
        present(safariVC, animated: true)
    }
}

