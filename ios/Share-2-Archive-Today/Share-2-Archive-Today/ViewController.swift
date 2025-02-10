//
//  ViewController.swift
//  Share-2-Archive-Today
//
//  Created by Gabirel Fair on 2/9/25.
//

import UIKit
import SafariServices

class ViewController: UIViewController {
    @IBOutlet weak var tableView: UITableView!
    
    private var urls: [String] = []
    private let urlStore = URLStore.shared
    
    // MARK: - Lifecycle Methods
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        setupTableView()
        setupNavigationBar()
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        refreshUrls()
    }
    
    // MARK: - Setup Methods
    
    private func setupUI() {
        view.backgroundColor = .systemBackground
    }
    
    private func setupTableView() {
        tableView.delegate = self
        tableView.dataSource = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "URLCell")
        
        // Add refresh control
        let refreshControl = UIRefreshControl()
        refreshControl.addTarget(self, action: #selector(refreshUrls), for: .valueChanged)
        tableView.refreshControl = refreshControl
        
        // Configure table view appearance
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 60
        tableView.separatorStyle = .singleLine
        tableView.separatorInset = UIEdgeInsets(top: 0, left: 15, bottom: 0, right: 15)
    }
    
    private func setupNavigationBar() {
        navigationItem.title = "Saved URLs"
        navigationController?.navigationBar.prefersLargeTitles = true
        
        // Add edit button
        navigationItem.rightBarButtonItem = editButtonItem
        
        // Add clear all button
        let clearButton = UIBarButtonItem(title: "Clear All",
                                        style: .plain,
                                        target: self,
                                        action: #selector(clearAllTapped))
        navigationItem.leftBarButtonItem = clearButton
    }
    
    // MARK: - Action Methods
    
    @objc private func refreshUrls() {
        urls = urlStore.getSavedURLs().reversed() // Show newest first
        tableView.reloadData()
        tableView.refreshControl?.endRefreshing()
    }
    
    @objc private func clearAllTapped() {
        let alert = UIAlertController(
            title: "Clear All URLs",
            message: "Are you sure you want to delete all saved URLs? This action cannot be undone.",
            preferredStyle: .alert
        )
        
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Clear All", style: .destructive) { [weak self] _ in
            self?.clearAllUrls()
        })
        
        present(alert, animated: true)
    }
    
    private func clearAllUrls() {
        let defaults = UserDefaults(suiteName: "group.org.Gnosco.Share-2-Archive-Today")
        defaults?.removeObject(forKey: "saved_urls")
        defaults?.synchronize()
        refreshUrls()
    }
    
    override func setEditing(_ editing: Bool, animated: Bool) {
        super.setEditing(editing, animated: animated)
        tableView.setEditing(editing, animated: animated)
    }
    
    // MARK: - URL Handling Methods
    
    private func openUrl(_ urlString: String) {
        guard let url = URL(string: urlString) else {
            showErrorAlert(message: "Invalid URL format")
            return
        }
        
        let safariVC = SFSafariViewController(url: url)
        safariVC.preferredControlTintColor = .systemBlue
        present(safariVC, animated: true)
    }
    
    private func openInArchiveToday(_ urlString: String) {
        guard let encodedUrl = urlString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let archiveUrl = URL(string: "https://archive.today/?run=1&url=\(encodedUrl)") else {
            showErrorAlert(message: "Could not create archive URL")
            return
        }
        
        let safariVC = SFSafariViewController(url: archiveUrl)
        safariVC.preferredControlTintColor = .systemBlue
        present(safariVC, animated: true)
    }
    
    private func shareUrl(_ urlString: String) {
        guard let url = URL(string: urlString) else { return }
        
        let activityVC = UIActivityViewController(
            activityItems: [url],
            applicationActivities: nil
        )
        
        // For iPad
        if let popoverController = activityVC.popoverPresentationController {
            popoverController.sourceView = view
            popoverController.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 0, height: 0)
            popoverController.permittedArrowDirections = []
        }
        
        present(activityVC, animated: true)
    }
    
    // MARK: - Helper Methods
    
    private func showErrorAlert(message: String) {
        let alert = UIAlertController(
            title: "Error",
            message: message,
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}

// MARK: - UITableViewDataSource

extension ViewController: UITableViewDataSource {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if urls.isEmpty {
            let messageLabel = UILabel()
            messageLabel.text = "No saved URLs yet\nShared URLs will appear here"
            messageLabel.textAlignment = .center
            messageLabel.textColor = .secondaryLabel
            messageLabel.numberOfLines = 0
            messageLabel.font = .preferredFont(forTextStyle: .body)
            tableView.backgroundView = messageLabel
        } else {
            tableView.backgroundView = nil
        }
        return urls.count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "URLCell", for: indexPath)
        
        let url = urls[indexPath.row]
        cell.textLabel?.text = url
        cell.textLabel?.numberOfLines = 0
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.accessoryType = .disclosureIndicator
        
        return cell
    }
    
    func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) {
        if editingStyle == .delete {
            let urlToDelete = urls[indexPath.row]
            urls.remove(at: indexPath.row)
            
            var savedUrls = urlStore.getSavedURLs()
            if let index = savedUrls.firstIndex(of: urlToDelete) {
                savedUrls.remove(at: index)
                UserDefaults(suiteName: "group.org.Gnosco.Share-2-Archive-Today")?.set(savedUrls, forKey: "saved_urls")
            }
            
            tableView.deleteRows(at: [indexPath], with: .fade)
        }
    }
}

// MARK: - UITableViewDelegate

extension ViewController: UITableViewDelegate {
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        
        let url = urls[indexPath.row]
        let alert = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        
        // Open in Archive.today
        alert.addAction(UIAlertAction(title: "Open in Archive.today", style: .default) { [weak self] _ in
            self?.openInArchiveToday(url)
        })
        
        // Open original URL
        alert.addAction(UIAlertAction(title: "Open Original URL", style: .default) { [weak self] _ in
            self?.openUrl(url)
        })
        
        // Share URL
        alert.addAction(UIAlertAction(title: "Share URL", style: .default) { [weak self] _ in
            self?.shareUrl(url)
        })
        
        // Copy URL
        alert.addAction(UIAlertAction(title: "Copy URL", style: .default) { _ in
            UIPasteboard.general.string = url
        })
        
        // Delete URL
        alert.addAction(UIAlertAction(title: "Delete", style: .destructive) { [weak self] _ in
            self?.tableView(tableView, commit: .delete, forRowAt: indexPath)
        })
        
        // Cancel
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        
        // For iPad
        if let popoverController = alert.popoverPresentationController {
            if let cell = tableView.cellForRow(at: indexPath) {
                popoverController.sourceView = cell
                popoverController.sourceRect = cell.bounds
            }
        }
        
        present(alert, animated: true)
    }
}

