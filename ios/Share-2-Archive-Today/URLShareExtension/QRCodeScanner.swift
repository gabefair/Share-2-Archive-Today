import UIKit
import Vision
import os.log

/// Utility class for scanning QR codes from images
class QRCodeScanner {
    
    /// Logger for debugging
    private static let logger = Logger(subsystem: "org.Gnosco.Share-2-Archive-Today", category: "QRCodeScanner")
    
    /// Scans an image for QR codes and extracts URLs
    /// - Parameters:
    ///   - image: The image to scan for QR codes
    ///   - completion: Completion handler that returns an optional URL string if found
    static func scanQRCode(from image: UIImage, completion: @escaping (String?) -> Void) {
        guard let cgImage = image.cgImage else {
            logger.error("Failed to get CGImage from UIImage")
            completion(nil)
            return
        }
        
        // Log image dimensions and orientation for debugging
        logger.info("Scanning image with dimensions: \(image.size.width) x \(image.size.height), orientation: \(image.imageOrientation.rawValue)")
        
        // Create a barcode detection request
        let request = VNDetectBarcodesRequest { request, error in
            if let error = error {
                logger.error("QR code scanning error: \(error.localizedDescription)")
                completion(nil)
                return
            }
            
            guard let results = request.results as? [VNBarcodeObservation], !results.isEmpty else {
                logger.warning("No barcode results found")
                
                // Try with different orientation if initial scan fails
                tryScanningWithDifferentOrientation(image: image, completion: completion)
                return
            }
            
            logger.info("Found \(results.count) barcode results")
            
            // Process all found barcodes
            processScannedBarcodes(results, completion: completion)
        }
        
        // Configure request for better detection
        request.symbologies = [.qr, .aztec, .code128, .dataMatrix, .pdf417, .code39, .code39Checksum, .code39FullASCII, .code93, .ean13]
        
        let handler = VNImageRequestHandler(cgImage: cgImage, orientation: mapToVNImageOrientation(image.imageOrientation), options: [:])
        
        do {
            logger.info("Starting barcode detection...")
            try handler.perform([request])
        } catch {
            logger.error("Failed to perform QR code detection: \(error.localizedDescription)")
            
            // Try with different orientation if initial scan fails
            tryScanningWithDifferentOrientation(image: image, completion: completion)
        }
    }
    
    /// Processes the barcode detection results
    /// - Parameters:
    ///   - results: Array of barcode observations
    ///   - completion: Completion handler with optional URL string
    private static func processScannedBarcodes(_ results: [VNBarcodeObservation], completion: @escaping (String?) -> Void) {
        // First try to find QR codes
        for result in results {
            if result.symbology == .qr {
                logger.info("Found QR code with confidence: \(result.confidence)")
                
                if let payloadString = result.payloadStringValue {
                    logger.info("QR code payload: \(payloadString)")
                    
                    // Try to process the payload as a URL
                    if let url = processPayloadAsURL(payloadString) {
                        logger.info("Extracted URL from QR code: \(url)")
                        completion(url)
                        return
                    }
                }
            }
        }
        
        // If no QR codes with URLs were found, try other barcode types
        for result in results {
            if result.symbology != .qr, let payloadString = result.payloadStringValue {
                logger.info("Found non-QR barcode with payload: \(payloadString)")
                
                if let url = processPayloadAsURL(payloadString) {
                    logger.info("Extracted URL from non-QR barcode: \(url)")
                    completion(url)
                    return
                }
            }
        }
        
        // If still nothing found, just take the first payload regardless of type
        for result in results {
            if let payloadString = result.payloadStringValue, !payloadString.isEmpty {
                logger.info("Using raw payload as potential URL: \(payloadString)")
                completion(payloadString)
                return
            }
        }
        
        logger.warning("No usable payloads found in barcodes")
        completion(nil)
    }
    
    /// Processes a string payload to extract or format as a URL
    /// - Parameter payload: The string payload from a barcode
    /// - Returns: A formatted URL string or nil if not processable
    private static func processPayloadAsURL(_ payload: String) -> String? {
        // Step 1: Check if it's already a valid URL with scheme
        if let url = URL(string: payload), url.scheme != nil {
            logger.info("Payload is already a valid URL: \(payload)")
            return payload
        }
        
        // Step 2: Try to extract URL using URLProcessor
        if let extractedURL = URLProcessor.extractURL(from: payload) {
            logger.info("Extracted URL from payload: \(extractedURL)")
            return extractedURL
        }
        
        // Step 3: Check if it could be a URL without scheme
        if payload.contains(".") {
            let components = payload.components(separatedBy: ".")
            if components.count >= 2 && components[1].count >= 2 {
                // Looks like a domain, add https
                let urlWithScheme = "https://\(payload)"
                logger.info("Added https:// to potential domain: \(urlWithScheme)")
                return urlWithScheme
            }
        }
        
        // Step 4: Check for common URL TLDs
        let commonTLDs = [".com", ".org", ".net", ".edu", ".gov", ".io", ".co", ".app"]
        for tld in commonTLDs {
            if payload.lowercased().contains(tld) {
                // Contains a TLD, likely a URL
                let urlWithScheme = payload.hasPrefix("http") ? payload : "https://\(payload)"
                logger.info("Found TLD in payload, adding scheme: \(urlWithScheme)")
                return urlWithScheme
            }
        }
        
        // Could not process as URL
        return nil
    }
    
    /// Try scanning the image with different orientations if initial scan fails
    /// - Parameters:
    ///   - image: The image to scan
    ///   - completion: Completion handler with optional URL string
    private static func tryScanningWithDifferentOrientation(image: UIImage, completion: @escaping (String?) -> Void) {
        logger.info("Trying scan with different orientation...")
        
        // Create rotated image
        if let rotatedImage = rotateImage(image, byDegrees: 90) {
            guard let cgImage = rotatedImage.cgImage else {
                logger.error("Failed to get CGImage from rotated image")
                completion(nil)
                return
            }
            
            let request = VNDetectBarcodesRequest { request, error in
                if let error = error {
                    logger.error("QR code scanning error with rotated image: \(error.localizedDescription)")
                    completion(nil)
                    return
                }
                
                guard let results = request.results as? [VNBarcodeObservation], !results.isEmpty else {
                    logger.warning("No barcode results found in rotated image")
                    completion(nil)
                    return
                }
                
                processScannedBarcodes(results, completion: completion)
            }
            
            request.symbologies = [.qr, .aztec, .code128, .dataMatrix]
            
            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            
            do {
                try handler.perform([request])
            } catch {
                logger.error("Failed to perform QR code detection on rotated image: \(error.localizedDescription)")
                completion(nil)
            }
        } else {
            logger.error("Failed to rotate image")
            completion(nil)
        }
    }
    
    /// Rotates an image by the specified degrees
    /// - Parameters:
    ///   - image: The image to rotate
    ///   - degrees: Rotation in degrees
    /// - Returns: Rotated UIImage or nil if rotation failed
    private static func rotateImage(_ image: UIImage, byDegrees degrees: CGFloat) -> UIImage? {
        let radians = degrees * .pi / 180.0
        let size = image.size
        
        UIGraphicsBeginImageContextWithOptions(size, false, image.scale)
        let context = UIGraphicsGetCurrentContext()
        
        // Move origin to center
        context?.translateBy(x: size.width/2, y: size.height/2)
        // Rotate
        context?.rotate(by: radians)
        // Move origin back
        context?.translateBy(x: -size.width/2, y: -size.height/2)
        
        image.draw(at: .zero)
        let rotatedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        
        return rotatedImage
    }
    
    /// Maps UIImage orientation to VNImageRequestHandler orientation
    /// - Parameter orientation: UIImage orientation
    /// - Returns: Corresponding CGImagePropertyOrientation
    private static func mapToVNImageOrientation(_ orientation: UIImage.Orientation) -> CGImagePropertyOrientation {
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
}
