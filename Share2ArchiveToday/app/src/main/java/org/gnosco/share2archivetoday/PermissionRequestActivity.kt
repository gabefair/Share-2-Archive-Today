package org.gnosco.share2archivetoday

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Activity to request necessary permissions for the app to function properly
 */
class PermissionRequestActivity : Activity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, proceed to main app
            proceedToMainApp()
        } else {
            // Permission denied, show explanation and settings option
            showPermissionDeniedDialog()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if we need to request permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Permission already granted, proceed
                proceedToMainApp()
            } else {
                // Request permission
                requestNotificationPermission()
            }
        } else {
            // No notification permission needed on older Android versions
            proceedToMainApp()
        }
    }
    
    private fun requestNotificationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )) {
            // Show explanation to user
            showPermissionExplanation()
        } else {
            // Request permission directly
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    private fun showPermissionExplanation() {
        // Show a simple dialog explaining why we need notification permission
        android.app.AlertDialog.Builder(this)
            .setTitle("Notification Permission Needed")
            .setMessage("This app needs notification permission to show you important information about your downloads and app usage. Without this permission, you may miss important updates.")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("Not Now") { _, _ ->
                // Proceed without permission (user can still use the app)
                proceedToMainApp()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showPermissionDeniedDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("Notification permission was denied. You can still use the app, but you may miss important information. You can enable notifications later in Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Continue Anyway") { _, _ ->
                proceedToMainApp()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
            proceedToMainApp()
        }
    }
    
    private fun proceedToMainApp() {
        // Check if we have a share intent to handle
        if (intent?.action == Intent.ACTION_SEND) {
            // Forward the share intent to the appropriate activity
            val targetActivity = when {
                intent.type?.startsWith("text/plain") == true -> {
                    // Determine which activity to use based on user preference or URL type
                    if (isVideoUrl(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "")) {
                        VideoDownloadActivity::class.java
                    } else {
                        MainActivity::class.java
                    }
                }
                intent.type?.startsWith("image/") == true -> MainActivity::class.java
                else -> MainActivity::class.java
            }
            
            val newIntent = Intent(this, targetActivity).apply {
                action = intent.action
                type = intent.type
                putExtra(Intent.EXTRA_TEXT, intent.getStringExtra(Intent.EXTRA_TEXT))
                putExtra(Intent.EXTRA_STREAM, intent.getParcelableExtra(Intent.EXTRA_STREAM))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(newIntent)
        } else {
            // No share intent, just finish
            Toast.makeText(this, "Share 2 Archive Today is ready!", Toast.LENGTH_SHORT).show()
        }
        
        finish()
    }
    
    private fun isVideoUrl(url: String): Boolean {
        val videoHosts = listOf(
            "youtube.com", "youtu.be", "vimeo.com", "dailymotion.com", "twitch.tv",
            "facebook.com", "instagram.com", "twitter.com", "tiktok.com", "reddit.com"
        )
        val lowerUrl = url.lowercase()
        return videoHosts.any { host -> lowerUrl.contains(host) }
    }
}
