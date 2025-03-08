import UIKit
import SafariServices
import os.log

/// A view controller that displays an FAQ and contact information
class FAQViewController: UIViewController {
    
    // MARK: - Properties
    
    private let scrollView = UIScrollView()
    private let contentView = UIView()
    private let logger = Logger(subsystem: "org.Gnosco.Share-2-Archive-Today", category: "FAQViewController")
    
    // MARK: - Lifecycle Methods
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        title = "Help & FAQ"
        view.backgroundColor = .systemBackground
        
        setupScrollView()
        setupContentView()
        
        // Add close button
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .close,
            target: self,
            action: #selector(dismissViewController)
        )
        
        logger.debug("FAQ view controller loaded")
    }
    
    // MARK: - Setup Methods
    
    private func setupScrollView() {
        view.addSubview(scrollView)
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
    
    private func setupContentView() {
        scrollView.addSubview(contentView)
        contentView.translatesAutoresizingMaskIntoConstraints = false
        
        NSLayoutConstraint.activate([
            contentView.topAnchor.constraint(equalTo: scrollView.topAnchor),
            contentView.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor),
            contentView.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor),
            contentView.widthAnchor.constraint(equalTo: scrollView.widthAnchor)
        ])
        
        let stackView = UIStackView()
        stackView.axis = .vertical
        stackView.spacing = 24
        stackView.alignment = .fill
        stackView.translatesAutoresizingMaskIntoConstraints = false
        
        contentView.addSubview(stackView)
        
        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 24),
            stackView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 24),
            stackView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -24),
            stackView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -24)
        ])
        
        // Add FAQ items
        addFAQItem(to: stackView, question: "What is Share 2 Archive.today?", answer: "This app allows you to easily archive web pages using archive.today service. It helps preserve web content that might change or disappear in the future.")
        
        addFAQItem(to: stackView, question: "How do I archive a URL?", answer: "There are several ways:\n\n1. Use the share button in Safari or other apps and select 'Share 2 Archive.today'\n2. Share text containing a URL\n3. Share an image containing a QR code with a URL")
        
        addFAQItem(to: stackView, question: "Why archive URLs?", answer: "Archiving preserves web content in its current state, protecting against changes, removal or link rot. It's useful for research, citations, and preserving digital content.")
        
        addFAQItem(to: stackView, question: "What happens to the URLs I archive?", answer: "The URLs are sent to archive.today for archiving. A copy of the URL is saved in this app for your reference, but the actual archived content is stored on archive.today's servers.")
        
        addFAQItem(to: stackView, question: "Can I archive any URL?", answer: "Most URLs can be archived, but some websites may block archiving services. Additionally, websites requiring login or with dynamic content may not archive properly.")
        
        addFAQItem(to: stackView, question: "How do I contact support?", answer: "If you have any issues or questions, please contact us at support@gnosco.org.")
        
        // Add contact button
        let contactButton = UIButton(type: .system)
        contactButton.setTitle("Contact Support", for: .normal)
        contactButton.titleLabel?.font = UIFont.systemFont(ofSize: 18, weight: .medium)
        contactButton.backgroundColor = .systemBlue
        contactButton.setTitleColor(.white, for: .normal)
        contactButton.layer.cornerRadius = 12
        contactButton.contentEdgeInsets = UIEdgeInsets(top: 12, left: 20, bottom: 12, right: 20)
        contactButton.addTarget(self, action: #selector(contactSupport), for: .touchUpInside)
        contactButton.translatesAutoresizingMaskIntoConstraints = false
        
        let contactContainer = UIView()
        contactContainer.addSubview(contactButton)
        
        NSLayoutConstraint.activate([
            contactButton.topAnchor.constraint(equalTo: contactContainer.topAnchor, constant: 20),
            contactButton.centerXAnchor.constraint(equalTo: contactContainer.centerXAnchor),
            contactButton.bottomAnchor.constraint(equalTo: contactContainer.bottomAnchor, constant: -20)
        ])
        
        stackView.addArrangedSubview(contactContainer)
    }
    
    private func addFAQItem(to stackView: UIStackView, question: String, answer: String) {
        let containerView = UIView()
        
        let questionLabel = UILabel()
        questionLabel.text = question
        questionLabel.font = UIFont.systemFont(ofSize: 18, weight: .bold)
        questionLabel.textColor = .label
        questionLabel.numberOfLines = 0
        
        let answerLabel = UILabel()
        answerLabel.text = answer
        answerLabel.font = UIFont.systemFont(ofSize: 16)
        answerLabel.textColor = .secondaryLabel
        answerLabel.numberOfLines = 0
        
        containerView.addSubview(questionLabel)
        containerView.addSubview(answerLabel)
        
        questionLabel.translatesAutoresizingMaskIntoConstraints = false
        answerLabel.translatesAutoresizingMaskIntoConstraints = false
        
        NSLayoutConstraint.activate([
            questionLabel.topAnchor.constraint(equalTo: containerView.topAnchor),
            questionLabel.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
            questionLabel.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
            
            answerLabel.topAnchor.constraint(equalTo: questionLabel.bottomAnchor, constant: 8),
            answerLabel.leadingAnchor.constraint(equalTo: containerView.leadingAnchor),
            answerLabel.trailingAnchor.constraint(equalTo: containerView.trailingAnchor),
            answerLabel.bottomAnchor.constraint(equalTo: containerView.bottomAnchor)
        ])
        
        stackView.addArrangedSubview(containerView)
    }
    
    // MARK: - Action Methods
    
    @objc private func dismissViewController() {
        logger.debug("Dismissing FAQ view controller")
        dismiss(animated: true)
    }
    
    @objc private func contactSupport() {
        logger.info("Opening mail app for support contact")
        if let url = URL(string: "mailto:support@gnosco.org") {
            UIApplication.shared.open(url)
        }
    }
}
