import UIKit
import Vision
import os.log
import CoreServices

/// Utility class for scanning QR codes from images
class QRCodeScanner {
    
    /// Logger for debugging
    private static let logger = Logger(subsystem: "org.Gnosco.Share-2-Archive-Today", category: "QRCodeScanner")
    
    /// Scans an image for QR codes and extracts URLs
    /// - Parameters:
    ///   - image: The image to scan for QR codes
    ///   - completion: Completion handler that returns an optional URL string if found,
    ///                 or an array of URLs if multiple QR codes are detected
    static func scanQRCode(from image: UIImage, completion: @escaping (String?) -> Void) {
        scanQRCodeMultiple(from: image) { urls in
            // For backward compatibility, return the first URL found
            completion(urls.first)
        }
    }
    
    /// Scans an image for multiple QR codes and extracts all URLs
    /// - Parameters:
    ///   - image: The image to scan for QR codes
    ///   - completion: Completion handler that returns an array of URL strings
    static func scanQRCodeMultiple(from image: UIImage, completion: @escaping ([String]) -> Void) {
        guard let cgImage = image.cgImage else {
            logger.error("Failed to get CGImage from UIImage")
            completion([])
            return
        }
        
        // Create a higher quality image if needed
        var imageToProcess = cgImage
        // If image is small, try to scale it up for better QR code detection
        if cgImage.width < 300 || cgImage.height < 300 {
            if let scaledImage = scaleUpImage(image, toWidth: 600)?.cgImage {
                imageToProcess = scaledImage
                logger.info("Scaled up small image for better QR detection")
            }
        }
        
        let request = VNDetectBarcodesRequest { request, error in
            if let error = error {
                logger.error("QR code scanning error: \(error.localizedDescription)")
                completion([])
                return
            }
            
            guard let results = request.results as? [VNBarcodeObservation], !results.isEmpty else {
                logger.warning("No barcode results found")
                completion([])
                return
            }
            
            logger.info("Found \(results.count) barcode results")
            var detectedUrls: [String] = []
            
            // Process all detected QR codes
            for result in results {
                logger.debug("Found barcode of type: \(result.symbology.rawValue) with confidence: \(result.confidence)")
                
                // Filter for QR code types with good confidence
                if result.symbology == .qr && result.confidence > 0.9 {
                    if let payloadString = result.payloadStringValue {
                        logger.info("Found QR code with payload: \(payloadString)")
                        
                        // Try multiple approaches to extract a URL
                        if let url = extractURLFromQRPayload(payloadString) {
                            if !detectedUrls.contains(url) {
                                logger.info("Extracted URL from QR code: \(url)")
                                detectedUrls.append(url)
                            }
                        }
                    }
                }
            }
            
            // If we found no URLs, try again with a lower confidence threshold
            if detectedUrls.isEmpty {
                logger.info("Retrying with lower confidence threshold")
                for result in results where result.symbology == .qr {
                    if let payloadString = result.payloadStringValue {
                        if let url = extractURLFromQRPayload(payloadString) {
                            logger.info("Extracted URL with lower confidence: \(url)")
                            detectedUrls.append(url)
                            break  // Just get the first one in this case
                        }
                    }
                }
            }
            
            // Return all detected URLs
            completion(detectedUrls)
        }
        
        // Set accuracy level based on iOS version
        setOptimalRevision(for: request)
        
        let handler = VNImageRequestHandler(cgImage: imageToProcess, orientation: transformImageOrientation(image.imageOrientation), options: [:])
        
        do {
            try handler.perform([request])
        } catch {
            logger.error("Failed to perform QR code detection: \(error.localizedDescription)")
            completion([])
        }
    }
    
    /// Extracts a URL from a QR code payload string using multiple methods
    /// - Parameter payload: String data from the QR code
    /// - Returns: URL string if one was found
    private static func extractURLFromQRPayload(_ payload: String) -> String? {
        // Method 1: Direct URL extraction using URLProcessor
        if let url = URLProcessor.extractURL(from: payload) {
            return url
        }
        
        // Method 2: Check if the payload is already a valid URL
        if let url = URL(string: payload), url.scheme != nil {
            return payload
        }
        
        // Method 3: Look for URL patterns in the payload
        let urlDetector = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)
        if let detector = urlDetector {
            let matches = detector.matches(in: payload, range: NSRange(payload.startIndex..., in: payload))
            if let match = matches.first, let range = Range(match.range, in: payload) {
                let urlString = String(payload[range])
                return urlString
            }
        }
        
        // Method 4: Check for common URL formats without protocol
        let commonDomains = ["com", "org", "net", "io", "dev", "co", "app"]
        let words = payload.components(separatedBy: .whitespacesAndNewlines)
        for word in words {
            // Look for potential domain names
            if word.contains(".") {
                for domain in commonDomains {
                    if word.hasSuffix(".\(domain)") || word.contains(".\(domain)/") {
                        // Likely a domain without http/https
                        return "https://\(word)"
                    }
                }
            }
        }
        
        // If no URL patterns found, return nil
        return nil
    }
    
    /// Scales up an image for better QR code detection
    /// - Parameters:
    ///   - image: Original image
    ///   - width: Target width
    /// - Returns: Scaled image
    private static func scaleUpImage(_ image: UIImage, toWidth width: CGFloat) -> UIImage? {
        let scale = width / image.size.width
        let newHeight = image.size.height * scale
        
        UIGraphicsBeginImageContextWithOptions(CGSize(width: width, height: newHeight), false, 0)
        image.draw(in: CGRect(x: 0, y: 0, width: width, height: newHeight))
        let newImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        
        return newImage
    }
    
    /// Transforms UIImage orientation to CGImagePropertyOrientation
    /// - Parameter orientation: UIImage orientation
    /// - Returns: CGImagePropertyOrientation value
    private static func transformImageOrientation(_ orientation: UIImage.Orientation) -> CGImagePropertyOrientation {
        switch orientation {
        case .up: return .up
        case .down: return .down
        case .left: return .left
        case .right: return .right
        case .upMirrored: return .upMirrored
        case .downMirrored: return .downMirrored
        case .leftMirrored: return .leftMirrored
        case .rightMirrored: return .rightMirrored
        @unknown default: return .up
        }
    }
    
    /// Sets the optimal revision for barcode detection based on iOS version
    /// - Parameter request: The VNDetectBarcodesRequest to configure
    private static func setOptimalRevision(for request: VNDetectBarcodesRequest) {
        if #available(iOS 17.0, *){
            request.revision = VNDetectBarcodesRequestRevision4
            logger.debug("Using VNDetectBarcodesRequestRevision3 (iOS 17+)")
        } else if #available(iOS 16.0, *) {
            // Use the newest revision for iOS 16+
            request.revision = VNDetectBarcodesRequestRevision3
            logger.debug("Using VNDetectBarcodesRequestRevision3 (iOS 16+)")
        } else if #available(iOS 15.0, *) {
            // Use revision 2 for iOS 15
            request.revision = VNDetectBarcodesRequestRevision2
            logger.debug("Using VNDetectBarcodesRequestRevision2 (iOS 15)")
        } else {
            // For earlier iOS versions, no specific revision is set (uses default)
            logger.debug("Using default VNDetectBarcodesRequest revision (iOS < 15)")
        }
    }
}
