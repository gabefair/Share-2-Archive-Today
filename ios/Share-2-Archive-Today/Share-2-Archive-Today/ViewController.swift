//
//  ViewController.swift
//  Share-2-Archive-Today
//
import UIKit
import SafariServices

/// The main view controller for displaying and managing saved URLs.
/// Provides functionality to view, open, share, copy, and delete URLs as well as to open them via archive.today.
class ViewController: UIViewController {
    
    /// The table view that displays the saved URLs.
    @IBOutlet weak var tableView: UITableView!
    
    /// An array of URL strings loaded from persistent storage.
    private var urls: [String] = []
    
    /// Shared URL store instance for handling persistence.
    private let urlStore = URLStore.shared
    
    // MARK: - Lifecycle Methods
    
    /// Called after the view has been loaded into memory.
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        setupTableView()
        setupNavigationBar()
    }
    
    /// Called before the view appears on screen.
    /// Refreshes the list of URLs from persistent storage.
    /// - Parameter animated: A Boolean value indicating whether the appearance is animated.
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        refreshUrls()
    }
    
    // MARK: - Setup Methods
    
    /// Configures the basic UI appearance for the view.
    private func setupUI() {
        view.backgroundColor = .systemBackground
    }
    
    /// Configures the table view's delegate, data source, and appearance.
    private func setupTableView() {
        tableView.delegate = self
        tableView.dataSource = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "URLCell")
        
        // Add refresh control for pull-to-refresh functionality.
        let refreshControl = UIRefreshControl()
        refreshControl.addTarget(self, action: #selector(refreshUrls), for: .valueChanged)
        tableView.refreshControl = refreshControl
        
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 60
        tableView.separatorStyle = .singleLine
        tableView.separatorInset = UIEdgeInsets(top: 0, left: 15, bottom: 0, right: 15)
    }
    
    /// Configures the navigation bar including the title and bar button items.
    private func setupNavigationBar() {
        navigationItem.title = "Saved URLs"
        navigationController?.navigationBar.prefersLargeTitles = true
        
        // Add edit button for enabling deletion mode.
        navigationItem.rightBarButtonItem = editButtonItem
        
        // Add "Clear All" button for deleting all saved URLs.
        let clearButton = UIBarButtonItem(title: "Clear All",
                                          style: .plain,
                                          target: self,
                                          action: #selector(clearAllTapped))
        navigationItem.leftBarButtonItem = clearButton
    }
    
    // MARK: - Action Methods
    
    /// Refreshes the list of URLs by retrieving them from the URL store and reloading the table view.
    @objc private func refreshUrls() {
        urls = urlStore.getSavedURLs().reversed() // Show newest URLs first.
        tableView.reloadData()
        tableView.refreshControl?.endRefreshing()
    }
    
    /// Presents an alert asking the user to confirm deletion of all saved URLs.
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
    
    /// Clears all saved URLs from persistent storage.
    /// Note: Ideally, this functionality should be moved into URLStore as a dedicated method.
    private func clearAllUrls() {
        // Currently, we directly remove the key from UserDefaults.
        UserDefaults(suiteName: "group.org.Gnosco.Share-2-Archive-Today")?.removeObject(forKey: "saved_urls")
        refreshUrls()
    }
    
    /// Opens the given URL in an in-app Safari view.
    /// - Parameter urlString: The URL string to open.
    private func openUrl(_ urlString: String) {
        guard let url = URL(string: urlString) else {
            showErrorAlert(message: "Invalid URL format")
            return
        }
        
        let safariVC = SFSafariViewController(url: url)
        safariVC.preferredControlTintColor = .systemBlue
        present(safariVC, animated: true)
    }
    
    /// Opens the provided URL via archive.today.
    /// - Parameter urlString: The original URL string to archive.
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
    
    /// Presents a share sheet to allow the user to share the provided URL.
    /// - Parameter urlString: The URL string to share.
    private func shareUrl(_ urlString: String) {
        guard let url = URL(string: urlString) else { return }
        
        let activityVC = UIActivityViewController(
            activityItems: [url],
            applicationActivities: nil
        )
        
        // For iPad: Configure the popover presentation.
        if let popoverController = activityVC.popoverPresentationController {
            popoverController.sourceView = view
            popoverController.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 0, height: 0)
            popoverController.permittedArrowDirections = []
        }
        
        present(activityVC, animated: true)
    }
    
    /// Displays an error alert with the provided message.
    /// - Parameter message: The error message to display.
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
    
    /// Returns the number of rows in the table view.
    /// - Parameters:
    ///   - tableView: The table view requesting the information.
    ///   - section: The section index (only one section is used).
    /// - Returns: The number of saved URLs.
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
    
    /// Configures and returns the cell for the given index path.
    /// - Parameters:
    ///   - tableView: The table view requesting the cell.
    ///   - indexPath: The index path of the cell.
    /// - Returns: A configured table view cell displaying the URL.
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "URLCell", for: indexPath)
        let url = urls[indexPath.row]
        cell.textLabel?.text = url
        cell.textLabel?.numberOfLines = 0
        cell.textLabel?.font = .preferredFont(forTextStyle: .body)
        cell.accessoryType = .disclosureIndicator
        return cell
    }
    
    /// Enables deletion of a URL from the table view.
    /// - Parameters:
    ///   - tableView: The table view requesting the commit.
    ///   - editingStyle: The editing style for the row.
    ///   - indexPath: The index path of the row to be deleted.
    func tableView(_ tableView: UITableView,
                   commit editingStyle: UITableViewCell.EditingStyle,
                   forRowAt indexPath: IndexPath) {
        if editingStyle == .delete {
            // Use URLStore's method to remove the URL from persistent storage.
            urlStore.removeURL(at: indexPath.row)
            urls.remove(at: indexPath.row)
            tableView.deleteRows(at: [indexPath], with: .fade)
        }
    }
}

// MARK: - UITableViewDelegate

extension ViewController: UITableViewDelegate {
    
    /// Handles the selection of a URL from the table view.
    /// Presents an action sheet with options to open, share, copy, or delete the URL.
    /// - Parameters:
    ///   - tableView: The table view in which the selection occurred.
    ///   - indexPath: The index path of the selected row.
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let url = urls[indexPath.row]
        
        let alert = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        
        // Option to open in Archive.today.
        alert.addAction(UIAlertAction(title: "Open in Archive.today", style: .default) { [weak self] _ in
            self?.openInArchiveToday(url)
        })
        
        // Option to open the original URL.
        alert.addAction(UIAlertAction(title: "Open Original URL", style: .default) { [weak self] _ in
            self?.openUrl(url)
        })
        
        // Option to share the URL.
        alert.addAction(UIAlertAction(title: "Share URL", style: .default) { [weak self] _ in
            self?.shareUrl(url)
        })
        
        // Option to copy the URL to the clipboard.
        alert.addAction(UIAlertAction(title: "Copy URL", style: .default) { _ in
            UIPasteboard.general.string = url
        })
        
        // Option to delete the URL.
        alert.addAction(UIAlertAction(title: "Delete", style: .destructive) { [weak self] _ in
            self?.tableView(tableView, commit: .delete, forRowAt: indexPath)
        })
        
        // Cancel action.
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        
        // For iPad, present as a popover.
        if let popoverController = alert.popoverPresentationController,
           let cell = tableView.cellForRow(at: indexPath) {
            popoverController.sourceView = cell
            popoverController.sourceRect = cell.bounds
        }
        
        present(alert, animated: true)
    }
}
