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
        
        let request = VNDetectBarcodesRequest { request, error in
            if let error = error {
                logger.error("QR code scanning error: \(error.localizedDescription)")
                completion(nil)
                return
            }
            
            guard let results = request.results as? [VNBarcodeObservation] else {
                logger.warning("No barcode results found")
                completion(nil)
                return
            }
            
            logger.info("Found \(results.count) barcode results")
            
            // Look for QR codes
            for result in results {
                logger.debug("Found barcode: \(result.symbology.rawValue)")
                
                // Filter for QR code types
                if result.symbology == .qr {
                    if let payloadString = result.payloadStringValue {
                        logger.info("Found QR code with payload: \(payloadString)")
                        
                        // Check if the payload is a URL
                        if let url = URLProcessor.extractURL(from: payloadString) {
                            logger.info("Extracted URL from QR code: \(url)")
                            completion(url)
                            return
                        } else if let url = URL(string: payloadString), url.scheme != nil {
                            // If the payload is already a valid URL, use it directly
                            logger.info("Payload is already a valid URL: \(payloadString)")
                            completion(payloadString)
                            return
                        } else {
                            // Return the payload even if it's not in URL format
                            logger.info("Payload is not a URL, returning raw payload")
                            completion(payloadString)
                            return
                        }
                    }
                }
            }
            
            // No QR codes found with URLs
            logger.warning("No QR codes with URLs found")
            completion(nil)
        }
        
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        
        do {
            try handler.perform([request])
        } catch {
            logger.error("Failed to perform QR code detection: \(error.localizedDescription)")
            completion(nil)
        }
    }
}
