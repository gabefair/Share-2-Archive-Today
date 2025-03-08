import UIKit
import SafariServices

class ViewController: UIViewController {
    
    // MARK: - IBOutlets
    
    /// Table view displaying the list of archived URLs
    @IBOutlet private weak var tableView: UITableView!
    
    /// View displayed when there are no archived URLs
    @IBOutlet private weak var emptyStateView: UIView!
    
    // MARK: - Properties
    
    /// Array of saved URL strings, displayed in reverse chronological order
    private var urls: [String] = []
    
    /// Shared instance of URLStore for managing saved URLs
    private let urlStore = URLStore.shared
    
    // MARK: - Lifecycle Methods
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupRefreshControl()
        
        // Check if we should show the welcome message
        checkForFirstLaunch()
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        refreshUrls()
    }
    
    /// Checks if this is the first launch and shows welcome overlay if needed
    private func checkForFirstLaunch() {
        let welcomeShownKey = "org.Gnosco.Share-2-Archive-Today.welcomeShown"
        let welcomeShown = UserDefaults.standard.bool(forKey: welcomeShownKey)
        
        if !welcomeShown {
            // Wait for the view to fully load before showing welcome overlay
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
                self?.showWelcomeMessage()
                UserDefaults.standard.set(true, forKey: welcomeShownKey)
            }
        }
    }
    
    /// Shows the welcome overlay to first-time users
    private func showWelcomeMessage() {
        let welcomeView = WelcomeOverlayView(frame: .zero)
        welcomeView.onDismiss = { [weak self] in
            // Refresh URLs after welcome is dismissed to ensure sample URL is shown
            self?.refreshUrls()
        }
        welcomeView.show(in: self.view)
    }
    
    // MARK: - Setup Methods
    
    /// Sets up the pull-to-refresh control for the table view
    private func setupRefreshControl() {
        let refreshControl = UIRefreshControl()
        refreshControl.addTarget(self, action: #selector(refreshUrls), for: .valueChanged)
        tableView.refreshControl = refreshControl
    }
    
    // MARK: - URL Management Methods
    
    /// Opens a URL in Archive.today service
    /// - Parameter urlString: The URL to archive
    private func openInArchiveToday(_ urlString: String) {
        // Create URL components for the archive.today service
        guard var components = URLComponents(string: "https://archive.today/") else {
            showError(message: "Could not create archive URL")
            return
        }
        
        // Add query items - this properly encodes the URL as a parameter
        components.queryItems = [
            URLQueryItem(name: "run", value: "1"),
            URLQueryItem(name: "url", value: urlString)
        ]
        
        // Get the final URL
        guard let archiveUrl = components.url else {
            showError(message: "Could not create archive URL")
            return
        }
        
        let safariVC = SFSafariViewController(url: archiveUrl)
        safariVC.preferredControlTintColor = .systemBlue
        present(safariVC, animated: true)
    }
    
    /// Opens the original URL in Safari
    /// - Parameter urlString: The URL to open
    private func openOriginalUrl(_ urlString: String) {
        // No need to process the URL again - it was processed when saved to URLStore
        guard let url = URL(string: urlString) else {
            showError(message: "Invalid URL")
            return
        }
        
        let safariVC = SFSafariViewController(url: url)
        safariVC.preferredControlTintColor = .systemBlue
        present(safariVC, animated: true)
    }
    
    // MARK: - UI Update Methods
    
    /// Updates the UI based on whether there are any URLs
    private func updateEmptyState() {
        emptyStateView.isHidden = !urls.isEmpty
        tableView.isHidden = urls.isEmpty
    }
    
    // MARK: - IBActions
    
    /// Handles the Clear All button tap
    /// - Parameter sender: The button that triggered the action
    @IBAction func clearAllTapped(_ sender: Any) {
        let alert = UIAlertController(
            title: "Clear All URLs",
            message: "Are you sure you want to delete all saved URLs? This action cannot be undone.",
            preferredStyle: .alert
        )
        
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Clear All", style: .destructive) { [weak self] _ in
            self?.urlStore.clearAllURLs()
            self?.refreshUrls()
        })
        
        present(alert, animated: true)
    }
    
    /// Handles the Edit button tap
    /// - Parameter sender: The button that triggered the action
    @IBAction func editButtonTapped(_ sender: Any) {
        tableView.setEditing(!tableView.isEditing, animated: true)
    }
    
    /// Refreshes the list of URLs from storage
    @objc private func refreshUrls() {
        urls = urlStore.getSavedURLs().reversed()
        updateEmptyState()
        tableView.reloadData()
        tableView.refreshControl?.endRefreshing()
    }
    
    /// Shows an error alert to the user
    /// - Parameter message: The error message to display
    private func showError(message: String) {
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
        return urls.count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "URLCell", for: indexPath)
        cell.textLabel?.text = urls[indexPath.row]
        cell.textLabel?.numberOfLines = 0
        cell.accessoryType = .disclosureIndicator
        return cell
    }
    
    func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) {
        if editingStyle == .delete {
            let urlToDelete = urls[indexPath.row]
            urlStore.removeURL(urlToDelete)
            urls.remove(at: indexPath.row)
            tableView.deleteRows(at: [indexPath], with: .fade)
            updateEmptyState()
        }
    }
}

// MARK: - UITableViewDelegate

extension ViewController: UITableViewDelegate {
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        
        let url = urls[indexPath.row]
        let alert = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
        
        // View in Archive.today
        alert.addAction(UIAlertAction(title: "View in Archive.today", style: .default) { [weak self] _ in
            self?.openInArchiveToday(url)
        })
        
        // Open Original URL
        alert.addAction(UIAlertAction(title: "Open Original URL", style: .default) { [weak self] _ in
            self?.openOriginalUrl(url)
        })
        
        // Copy URL
        alert.addAction(UIAlertAction(title: "Copy URL", style: .default) { _ in
            UIPasteboard.general.string = url
        })
        
        // Delete
        alert.addAction(UIAlertAction(title: "Delete", style: .destructive) { [weak self] _ in
            guard let self = self else { return }
            self.tableView(tableView, commit: .delete, forRowAt: indexPath)
        })
        
        // Cancel
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        
        // iPad support
        if let popoverController = alert.popoverPresentationController {
            if let cell = tableView.cellForRow(at: indexPath) {
                popoverController.sourceView = cell
                popoverController.sourceRect = cell.bounds
            }
        }
        
        present(alert, animated: true)
    }
}
