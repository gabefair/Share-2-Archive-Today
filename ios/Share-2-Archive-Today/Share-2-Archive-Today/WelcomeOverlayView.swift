//
//  WelcomeOverlayView.swift
//  Share-2-Archive-Today
import UIKit

class WelcomeOverlayView: UIView {
    
    // MARK: - UI Elements
    
    private let containerView: UIView = {
        let view = UIView()
        view.backgroundColor = .systemBackground
        view.layer.cornerRadius = 16
        view.layer.shadowColor = UIColor.black.cgColor
        view.layer.shadowOpacity = 0.2
        view.layer.shadowOffset = CGSize(width: 0, height: 2)
        view.layer.shadowRadius = 8
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }()
    
    private let titleLabel: UILabel = {
        let label = UILabel()
        label.text = "Together we can preserve history!"
        label.font = .systemFont(ofSize: 22, weight: .bold)
        label.textAlignment = .center
        label.numberOfLines = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()
    
    private let imageView: UIImageView = {
        let imageView = UIImageView()
        imageView.contentMode = .scaleAspectFit
        imageView.tintColor = .systemBlue
        imageView.image = UIImage(systemName: "archivebox.fill")
        imageView.translatesAutoresizingMaskIntoConstraints = false
        return imageView
    }()
    
    private let descriptionLabel: UILabel = {
        let label = UILabel()
        label.text = "This app helps you save web pages using archive.today for future reference.\n\nUse the share extension in Safari or other apps to quickly archive links you want to preserve."
        label.font = .systemFont(ofSize: 16)
        label.textColor = .secondaryLabel
        label.textAlignment = .center
        label.numberOfLines = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }()
    
    private let getStartedButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("Get Started", for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 18, weight: .semibold)
        button.backgroundColor = .systemBlue
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 12
        button.translatesAutoresizingMaskIntoConstraints = false
        return button
    }()
    
    // MARK: - Properties
    
    var onDismiss: (() -> Void)?
    
    // MARK: - Initialization
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupView()
    }
    
    // MARK: - Setup
    
    private func setupView() {
        backgroundColor = UIColor.black.withAlphaComponent(0.4)
        
        addSubview(containerView)
        containerView.addSubview(titleLabel)
        containerView.addSubview(imageView)
        containerView.addSubview(descriptionLabel)
        containerView.addSubview(getStartedButton)
        
        NSLayoutConstraint.activate([
            // Container constraints
            containerView.centerXAnchor.constraint(equalTo: centerXAnchor),
            containerView.centerYAnchor.constraint(equalTo: centerYAnchor),
            containerView.widthAnchor.constraint(equalTo: widthAnchor, multiplier: 0.85),
            containerView.heightAnchor.constraint(lessThanOrEqualTo: heightAnchor, multiplier: 0.7),
            
            // Image constraints
            imageView.topAnchor.constraint(equalTo: containerView.topAnchor, constant: 30),
            imageView.centerXAnchor.constraint(equalTo: containerView.centerXAnchor),
            imageView.heightAnchor.constraint(equalToConstant: 80),
            imageView.widthAnchor.constraint(equalToConstant: 80),
            
            // Title constraints
            titleLabel.topAnchor.constraint(equalTo: imageView.bottomAnchor, constant: 20),
            titleLabel.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 20),
            titleLabel.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -20),
            
            // Description constraints
            descriptionLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 20),
            descriptionLabel.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 20),
            descriptionLabel.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -20),
            
            // Button constraints
            getStartedButton.topAnchor.constraint(equalTo: descriptionLabel.bottomAnchor, constant: 30),
            getStartedButton.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 20),
            getStartedButton.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -20),
            getStartedButton.heightAnchor.constraint(equalToConstant: 50),
            getStartedButton.bottomAnchor.constraint(equalTo: containerView.bottomAnchor, constant: -30)
        ])
        
        // Add tap gesture to button
        getStartedButton.addTarget(self, action: #selector(dismissView), for: .touchUpInside)
    }
    
    // MARK: - Actions
    
    @objc private func dismissView() {
        animate(isShowing: false) { [weak self] in
            self?.onDismiss?()
            self?.removeFromSuperview()
        }
    }
    
    // MARK: - Animation
    
    func show(in parentView: UIView, completion: (() -> Void)? = nil) {
        parentView.addSubview(self)
        self.frame = parentView.bounds
        
        // Start with container scaled down
        containerView.transform = CGAffineTransform(scaleX: 0.8, y: 0.8)
        containerView.alpha = 0
        
        animate(isShowing: true, completion: completion)
    }
    
    private func animate(isShowing: Bool, completion: (() -> Void)? = nil) {
        let transform: CGAffineTransform = isShowing ? .identity : CGAffineTransform(scaleX: 0.8, y: 0.8)
        let alpha: CGFloat = isShowing ? 1.0 : 0.0
        
        UIView.animate(withDuration: 0.3, delay: 0, options: .curveEaseInOut, animations: {
            self.containerView.transform = transform
            self.containerView.alpha = alpha
            self.backgroundColor = isShowing ? UIColor.black.withAlphaComponent(0.4) : UIColor.clear
        }, completion: { _ in
            completion?()
        })
    }
}

