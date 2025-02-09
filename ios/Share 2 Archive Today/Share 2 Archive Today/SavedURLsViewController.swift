// SavedURLsViewController.swift
import UIKit

class SavedURLsViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    private let tableView = UITableView()
    private var savedURLs: [URL] = []
    private let urlHandler = URLHandlerService.shared
    private let appGroupID = "group.org.Gnosco.Share-2-Archive-Today"
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        loadSavedURLs()
    }
    
    private func setupUI() {
        title = "Saved URLs"
        view.backgroundColor = .systemBackground
        
        tableView.frame = view.bounds
        tableView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "URLCell")
        view.addSubview(tableView)
    }
    
    private func loadSavedURLs() {
            if let userDefaults = UserDefaults(suiteName: "group.org.Gnosco.Share-2-Archive-Today"),
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
            let urlStrings = savedURLs.map { $0.absoluteString }
            UserDefaults.standard.set(urlStrings, forKey: "SavedURLs")
            tableView.deleteRows(at: [indexPath], with: .fade)
        }
    }
}
