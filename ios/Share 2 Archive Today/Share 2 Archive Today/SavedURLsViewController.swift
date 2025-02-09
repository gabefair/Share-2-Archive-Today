// SavedURLsViewController.swift
import UIKit

class SavedURLsViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    private let tableView = UITableView()
    private var savedURLs: [URL] = []
    private let urlHandler = URLHandlerService.shared
    private let appGroupID = "group.org.Gnosco.Share-2-Archive-Today"
    
    // Tutorial view
    private let tutorialView: UIView = {
        let view = UIView()
        view.backgroundColor = .systemBackground
        view.layer.cornerRadius = 10
        view.layer.shadowColor = UIColor.black.cgColor
        view.layer.shadowOffset = CGSize(width: 0, height: 2)
        view.layer.shadowRadius = 4
        view.layer.shadowOpacity = 0.2
        return view
    }()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        
        // Delay the tutorial check to ensure view is properly laid out
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            self.checkFirstLaunch()
        }
        loadSavedURLs()
    }
    
    private func setupUI() {
            title = "Saved URLs"
            view.backgroundColor = .systemBackground
            
            // Setup TableView
            tableView.frame = view.bounds
            tableView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            tableView.dataSource = self
            tableView.delegate = self
            tableView.register(UITableViewCell.self, forCellReuseIdentifier: "URLCell")
            view.addSubview(tableView)
            
            // Add refresh control
            let refreshControl = UIRefreshControl()
            refreshControl.addTarget(self, action: #selector(refreshURLs), for: .valueChanged)
            tableView.refreshControl = refreshControl
        }
    
    private func checkFirstLaunch() {
        if let userDefaults = UserDefaults(suiteName: appGroupID) {
            let isFirstLaunch = !userDefaults.bool(forKey: "hasLaunchedBefore")
            if isFirstLaunch {
                userDefaults.set(true, forKey: "hasLaunchedBefore")
                userDefaults.synchronize()
                
                // Add example URLs
                let exampleURLs = [
                    "https://example.com/interesting-article",
                    "https://example.org/important-resource"
                ]
                userDefaults.set(exampleURLs, forKey: "SavedURLs")
                showTutorial()
                loadSavedURLs()
            }
        }
    }
    
    private func showTutorial() {
        let tutorialContent = """
        Welcome to Share 2 Archive Today!

        How to use:
        1. While browsing, tap the Share button
        2. Select "Share 2 Archive Today" from the menu
        3. The URL will be archived automatically
        
        Your archived links will appear in this list.
        
        The example URLs below show how your saved links will appear.
        """
        
        let tutorialLabel = UILabel()
        tutorialLabel.text = tutorialContent
        tutorialLabel.numberOfLines = 0
        tutorialLabel.textAlignment = .left
        tutorialLabel.font = .systemFont(ofSize: 16)
        
        let dismissButton = UIButton(type: .system)
        dismissButton.setTitle("Got it!", for: .normal)
        dismissButton.addTarget(self, action: #selector(dismissTutorial), for: .touchUpInside)
        
        // Remove any existing tutorial view
       tutorialView.subviews.forEach { $0.removeFromSuperview() }
       
       tutorialView.addSubview(tutorialLabel)
       tutorialView.addSubview(dismissButton)
       view.addSubview(tutorialView)
       
       // Layout constraints
       tutorialView.translatesAutoresizingMaskIntoConstraints = false
       tutorialLabel.translatesAutoresizingMaskIntoConstraints = false
       dismissButton.translatesAutoresizingMaskIntoConstraints = false
       
       NSLayoutConstraint.activate([
           tutorialView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
           tutorialView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
           tutorialView.widthAnchor.constraint(equalTo: view.widthAnchor, multiplier: 0.85),
           
           tutorialLabel.topAnchor.constraint(equalTo: tutorialView.topAnchor, constant: 20),
           tutorialLabel.leadingAnchor.constraint(equalTo: tutorialView.leadingAnchor, constant: 20),
           tutorialLabel.trailingAnchor.constraint(equalTo: tutorialView.trailingAnchor, constant: -20),
           
           dismissButton.topAnchor.constraint(equalTo: tutorialLabel.bottomAnchor, constant: 20),
           dismissButton.centerXAnchor.constraint(equalTo: tutorialView.centerXAnchor),
           dismissButton.bottomAnchor.constraint(equalTo: tutorialView.bottomAnchor, constant: -20)
       ])
       
       // Add initial animation
       tutorialView.alpha = 0
       UIView.animate(withDuration: 0.3) {
           self.tutorialView.alpha = 1
       }
    }
    
    @objc private func dismissTutorial() {
        UIView.animate(withDuration: 0.3) {
            self.tutorialView.alpha = 0
        } completion: { _ in
            self.tutorialView.removeFromSuperview()
        }
    }
    
    @objc private func refreshURLs() {
            loadSavedURLs()
            tableView.refreshControl?.endRefreshing()
        }
        
        private func loadSavedURLs() {
            if let userDefaults = UserDefaults(suiteName: appGroupID),
               let urls = userDefaults.stringArray(forKey: "SavedURLs") {
                savedURLs = urls.compactMap { URL(string: $0) }
                tableView.reloadData()
            }
        }
    
    // UITableViewDataSource
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return savedURLs.count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "URLCell", for: indexPath)
        cell.textLabel?.text = savedURLs[indexPath.row].absoluteString
        cell.textLabel?.numberOfLines = 0
        return cell
    }
    
    // UITableViewDelegate
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        UIApplication.shared.open(savedURLs[indexPath.row])
    }
    
    func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) {
        if editingStyle == .delete {
            savedURLs.remove(at: indexPath.row)
            if let userDefaults = UserDefaults(suiteName: appGroupID) {
                let urlStrings = savedURLs.map { $0.absoluteString }
                userDefaults.set(urlStrings, forKey: "SavedURLs")
            }
            tableView.deleteRows(at: [indexPath], with: .fade)
        }
    }
}
