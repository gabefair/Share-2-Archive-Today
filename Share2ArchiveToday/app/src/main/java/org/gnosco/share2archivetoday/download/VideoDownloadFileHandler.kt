package org.gnosco.share2archivetoday.download

import android.app.Activity
import org.gnosco.share2archivetoday.utils.VideoFileOperationsHelper

/**
 * Handles file operations for downloaded videos (sharing, opening)
 * Delegates to VideoFileOperationsHelper for the actual operations
 */
class VideoDownloadFileHandler(
    private val activity: Activity
) {
    private val fileOperationsHelper = VideoFileOperationsHelper(activity)
    
    /**
     * Share a video file, excluding the VideoDownloadActivity from the chooser
     */
    fun shareVideoFile(filePath: String, title: String) {
        fileOperationsHelper.shareVideo(
            filePath = filePath,
            title = title,
            excludeActivity = VideoDownloadActivity::class.java,
            onComplete = { activity.finish() },
            onError = { activity.finish() }
        )
    }
    
    /**
     * Open a video file with the default video player
     */
    fun openVideoFile(filePath: String) {
        fileOperationsHelper.openVideo(
            filePath = filePath,
            onComplete = { activity.finish() },
            onError = { activity.finish() }
        )
    }
}

