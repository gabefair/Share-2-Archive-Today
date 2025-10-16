package org.gnosco.share2archivetoday

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Manages runtime permissions for video downloading functionality
 */
class PermissionManager(private val context: Context) {
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001

        // Required permissions for different Android versions
        // - On Android 13+ (TIRAMISU): only POST_NOTIFICATIONS (writing to MediaStore needs no storage permission)
        // - On Android 10-12: no runtime storage permissions required for MediaStore writes
        // - On Android 9 and below: WRITE_EXTERNAL_STORAGE needed when targeting public storage
        private val REQUIRED_PERMISSIONS: Array<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.POST_NOTIFICATIONS
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> emptyArray()
            else -> arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
    
    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if specific permission is granted
     */
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Request permissions if not granted
     * @param activity The activity to show permission dialog
     * @return true if all permissions are already granted, false if request was made
     */
    fun requestPermissionsIfNeeded(activity: Activity): Boolean {
        if (hasAllPermissions()) {
            return true
        }
        
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter { permission ->
            !hasPermission(permission)
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest,
                PERMISSION_REQUEST_CODE
            )
            return false
        }
        
        return true
    }
    
    /**
     * Handle permission request result
     * @param requestCode The request code from onRequestPermissionsResult
     * @param permissions The requested permissions
     * @param grantResults The grant results
     * @return true if all permissions were granted, false otherwise
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            return grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        }
        return false
    }
    
    /**
     * Get the list of missing permissions
     */
    fun getMissingPermissions(): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            !hasPermission(permission)
        }
    }
    
    /**
     * Check if we should show rationale for any permission
     */
    fun shouldShowRationale(activity: Activity): Boolean {
        return REQUIRED_PERMISSIONS.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }
    
    /**
     * Get user-friendly permission names
     */
    fun getPermissionNames(): List<String> {
        return REQUIRED_PERMISSIONS.map { permission ->
            when (permission) {
                Manifest.permission.POST_NOTIFICATIONS -> "Show notifications"
                Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Save files"
                else -> permission
            }
        }
    }
}
