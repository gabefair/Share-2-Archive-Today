package org.gnosco.share2archivetoday

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import org.gnosco.share2archivetoday.BackgroundDownloadService

/**
 * Test activity for debugging video download functionality
 * This can be used to test the implementation without going through the share menu
 */
class VideoDownloadTestActivity : Activity() {
    
    companion object {
        private const val TAG = "VideoDownloadTest"
    }
    
    private lateinit var videoDownloader: PythonVideoDownloader
    private val testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize video downloader (using Python implementation)
        videoDownloader = PythonVideoDownloader(applicationContext)

        // Run tests - finish immediately since tests run in background
        runTests()

        // Finish immediately to comply with Theme.NoDisplay: must call finish before onResume completes
        // Tests will run in background via coroutine
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't cancel the test scope - let tests complete in background
        // The scope will be garbage collected when tests finish
    }
    
    private fun runTests() {
        testScope.launch {
            try {
                // Test 1: Python functionality
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Testing Python functionality...", Toast.LENGTH_SHORT).show()
                }

                val pythonTest = videoDownloader.testPythonFunctionality()
                Log.d(TAG, "Python functionality test: $pythonTest")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "Python test: ${if (pythonTest) "PASSED" else "FAILED"}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                if (!pythonTest) {
                    Log.e(TAG, "Python test FAILED - stopping tests")
                    finishTests()
                    return@launch
                }

                // Test 2: Video info extraction
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Testing video info extraction...", Toast.LENGTH_SHORT).show()
                }

                val testUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ" // Rick Roll for testing
                Log.d(TAG, "Testing video info extraction for URL: $testUrl")
                
                try {
                    val videoInfo = videoDownloader.getVideoInfo(testUrl)

                    if (videoInfo != null) {
                        Log.d(TAG, "Video info test: SUCCESS")
                        Log.d(TAG, "Title: ${videoInfo.title}")
                        Log.d(TAG, "Duration: ${videoInfo.duration} seconds")
                        Log.d(TAG, "Uploader: ${videoInfo.uploader}")
                        Log.d(TAG, "View count: videoInfo.viewCount")
                        Log.d(TAG, "Description length: ${videoInfo.description.length} chars")
                        Log.d(TAG, "Thumbnail URL: ${videoInfo.thumbnail}")
                        Log.d(TAG, "Webpage URL: videoInfo.webpageUrl")

                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                applicationContext,
                                "Video info: ${videoInfo.title}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Log.e(TAG, "Video info test: FAILED - getVideoInfo returned null")
                        Log.e(TAG, "This could indicate:")
                        Log.e(TAG, "1. Python module not initialized properly")
                        Log.e(TAG, "2. yt-dlp not working correctly")
                        Log.e(TAG, "3. Network connectivity issues")
                        Log.e(TAG, "4. URL format not supported")
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                applicationContext,
                                "Video info extraction failed - check logs",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Video info test: EXCEPTION", e)
                    Log.e(TAG, "Exception details:")
                    Log.e(TAG, "  Message: ${e.message}")
                    Log.e(TAG, "  Cause: ${e.cause}")
                    Log.e(TAG, "  Stack trace: ${e.stackTraceToString()}")
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "Video info test failed with exception: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // Test 3: FFmpeg wrapper
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Testing FFmpeg wrapper...", Toast.LENGTH_SHORT).show()
                }

                try {
                    val ffmpegWrapper = FFmpegWrapper(applicationContext)
                    Log.d(TAG, "FFmpeg wrapper initialized successfully")
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "FFmpeg wrapper test: PASSED",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "FFmpeg wrapper test failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "FFmpeg wrapper test: FAILED",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // Test 4: Permission manager
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Testing permission manager...", Toast.LENGTH_SHORT).show()
                }

                val permissionManager = PermissionManager(applicationContext)
                val hasPermissions = permissionManager.hasAllPermissions()
                val missingPermissions = permissionManager.getMissingPermissions()

                Log.d(TAG, "Permission test: $hasPermissions")
                Log.d(TAG, "Missing permissions: $missingPermissions")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "Permissions: ${if (hasPermissions) "GRANTED" else "MISSING"}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // Test 5: Test with the problematic Reddit URL from logcat
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Testing Reddit URL...", Toast.LENGTH_SHORT).show()
                }

                val redditUrl = "https://www.reddit.com/r/CringeTikToks/comments/1o67bi7/former_cop_has_advice_for_ice_agents_violating/"
                Log.d(TAG, "Testing Reddit URL: $redditUrl")
                
                try {
                    val redditVideoInfo = videoDownloader.getVideoInfo(redditUrl)
                    
                    if (redditVideoInfo != null) {
                        Log.d(TAG, "Reddit video info test: SUCCESS")
                        Log.d(TAG, "Reddit title: ${redditVideoInfo.title}")
                        Log.d(TAG, "Reddit uploader: ${redditVideoInfo.uploader}")
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                applicationContext,
                                "Reddit test: ${redditVideoInfo.title}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Log.e(TAG, "Reddit video info test: FAILED")
                        Log.e(TAG, "This explains the format availability error in the logs")
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                applicationContext,
                                "Reddit test failed - format issue",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Reddit video info test: EXCEPTION", e)
                    Log.e(TAG, "This is likely the cause of the format availability error")
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "Reddit test exception: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // Test 6: Start Background service with Reddit URL to reproduce issue
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Starting background download test (Reddit)...", Toast.LENGTH_SHORT).show()
                }

                try {
                    BackgroundDownloadService.startDownload(
                        context = applicationContext,
                        url = redditUrl,
                        title = "Video from reddit.com",
                        uploader = "Unknown",
                        quality = "best"
                    )
                    Log.d(TAG, "Background download service started with Reddit URL")
                } catch (e: Exception) {
                    Log.e(TAG, "Background service start failed", e)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "All tests completed. Check logs for details.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                finishTests()

            } catch (e: Exception) {
                Log.e(TAG, "Test failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "Test failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                finishTests()
            }
        }
    }

    private fun finishTests() {
        // Delay a bit to let all Toast messages show, then finish
        testScope.launch {
            delay(3000) // Give more time for all tests to complete
            runOnUiThread {
                finish()
            }
        }
    }
}
