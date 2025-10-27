"""
Video downloader using yt-dlp
Main interface for video/audio downloads - refactored for modularity
"""

import os
import sys
import json
import traceback
from typing import Dict, Any, Optional, Callable

try:
    import yt_dlp
except ImportError as e:
    print(f"Failed to import yt_dlp: {e}", file=sys.stderr)
    raise

# Import our modular components
from ytdlp_utils import (
    debug_print, debug_print_stderr, ProgressTracker,
    create_result_dict, DEBUG_MODE
)
from ytdlp_format_processor import FormatProcessor
from ytdlp_audio_handler import AudioHandler
from ytdlp_video_handler import VideoHandler


class VideoDownloader:
    """Download videos using yt-dlp without requiring ffmpeg"""
    
    def __init__(self):
        self.cancelled = False
        self.format_processor = FormatProcessor()
        self.audio_handler = AudioHandler()
        self.video_handler = VideoHandler()
        
    def cancel(self):
        """Cancel the current download"""
        self.cancelled = True
        self.audio_handler.cancelled = True
        self.video_handler.cancelled = True
        
    def is_cancelled(self):
        """Check if download is cancelled"""
        return self.cancelled
    
    def reset_cancellation(self):
        """Reset the cancellation flag for a new download"""
        self.cancelled = False
        self.audio_handler.cancelled = False
        self.video_handler.cancelled = False
        
    def get_video_info(self, url: str) -> Dict[str, Any]:
        """
        Get video information without downloading
        
        Args:
            url: Video URL
            
        Returns:
            Dictionary with video info (title, uploader, formats, etc.)
        """
        try:
            ydl_opts = {
                'quiet': not DEBUG_MODE,
                'no_warnings': not DEBUG_MODE,
                'extract_flat': False,
                'dump_single_json': DEBUG_MODE,
                'ignoreerrors': True,
                'no_check_certificate': True,
                'prefer_insecure': False,
                'extractor_args': {
                    'reddit': {
                        'include': ['v.redd.it', 'i.redd.it', 'gfycat.com', 'imgur.com'],
                        'exclude': ['comments', 'user', 'subreddit']
                    }
                }
            }
            
            debug_print(f"[DEBUG] Getting video info for: {url}", flush=True)
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                
                if not info:
                    debug_print_stderr(f"[ERROR] Failed to extract video info from URL: {url}", flush=True)
                    return {
                        'title': 'Unknown',
                        'uploader': 'Unknown',
                        'duration': 0,
                        'formats': [],
                        'thumbnail': '',
                        'error': 'Failed to extract video information'
                    }
                
                # Log full JSON for debugging
                if DEBUG_MODE:
                try:
                    json_output = json.dumps(info, indent=2, default=str)
                        debug_print(f"[DEBUG] Full yt-dlp JSON output:", flush=True)
                    debug_print(json_output, flush=True)
                except Exception as json_e:
                    debug_print(f"[DEBUG] Could not serialize JSON: {json_e}", flush=True)
                
                # Extract format information
                formats = info.get('formats', [])
                debug_print(f"[DEBUG] Found {len(formats)} formats", flush=True)
                
                extracted_formats = self.format_processor.extract_format_info(formats)
                debug_print(f"[DEBUG] Extracted {len(extracted_formats)} formats for Kotlin", flush=True)
                
                result = {
                    'title': info.get('title', 'Unknown'),
                    'uploader': info.get('uploader', 'Unknown'),
                    'duration': info.get('duration', 0),
                    'formats': extracted_formats,
                    'thumbnail': info.get('thumbnail', ''),
                    'description': info.get('description', ''),
                    'extractor': info.get('extractor', 'unknown'),
                    'extractor_key': info.get('extractor_key', 'unknown'),
                    'webpage_url': info.get('webpage_url', url),
                }
                
                return result
                
        except Exception as e:
            error_msg = str(e)
            debug_print_stderr(f"Error getting video info: {error_msg}", flush=True)
            if DEBUG_MODE:
                traceback.print_exc(file=sys.stderr)
            
            return {
                'title': 'Unknown',
                'uploader': 'Unknown', 
                'duration': 0,
                'formats': [],
                'thumbnail': '',
                'error': f'Failed to extract video information: {error_msg}',
                'extractor': 'unknown',
                'extractor_key': 'unknown',
                'webpage_url': url,
            }
        
    def download_video(
        self,
        url: str,
        output_dir: str,
        quality: str = 'best',
        progress_callback: Optional[Callable] = None,
        format_id: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Download video without requiring ffmpeg

        Args:
            url: Video URL
            output_dir: Directory to save the video
            quality: Quality selection (e.g., 'best', '1080p', '720p', 'worst')
            progress_callback: Callback function for progress updates
            format_id: DEPRECATED - Specific format ID (now ignored)

        Returns:
            Dictionary with download result
        """
        try:
            self.cancelled = False

            debug_print(f"[info] Starting download: {url}", flush=True)
            debug_print(f"[info] Output directory: {output_dir}", flush=True)
            debug_print(f"[info] Quality preference: {quality}", flush=True)
            if format_id:
                debug_print(f"[info] Note: format_id '{format_id}' is deprecated, using quality-based selection", flush=True)

            if DEBUG_MODE:
                self.video_handler.debug_available_formats(url)

            progress_tracker = ProgressTracker(progress_callback)

            # Get video info to understand available formats
            try:
                video_info = self.get_video_info(url)
                extractor = video_info.get('extractor', 'unknown')
                debug_print(f"[info] Video extractor: {extractor}", flush=True)

                formats = video_info.get('formats', [])
                format_analysis = self.format_processor.analyze_formats(formats)
                
                debug_print(f"[info] Format analysis: DASH={format_analysis['has_dash_streams']}, " +
                          f"Audio-only={format_analysis['has_audio_only']}, " +
                          f"Video-only={format_analysis['has_video_only']}", flush=True)
                
                if format_analysis['has_dash_streams'] or \
                   (format_analysis['has_video_only'] and format_analysis['has_audio_only']):
                    debug_print(f"[info] Using DASH-optimized strategy for complex streams", flush=True)
                    return self.video_handler.download_dash_video(url, output_dir, quality, progress_tracker)

            except Exception as e:
                debug_print(f"[warning] Could not get video info for optimization: {e}", flush=True)

            # Try standard download strategies
            return self.video_handler.download_video_with_strategies(
                url, output_dir, quality, progress_tracker, self.format_processor
            )

        except Exception as e:
            error_msg = str(e)
            debug_print_stderr(f"[error] Download failed: {error_msg}", flush=True)
            if DEBUG_MODE:
                traceback.print_exc(file=sys.stderr)
            return create_result_dict(success=False, error=error_msg)
        
    def download_audio(
        self,
        url: str,
        output_dir: str,
        audio_format: str = 'mp3',
        progress_callback: Optional[Callable] = None
    ) -> Dict[str, Any]:
        """
        Download audio only with fallback strategies
        
        Args:
            url: Video URL
            output_dir: Directory to save the audio
            audio_format: Audio format (mp3, aac, etc.)
            progress_callback: Callback function for progress updates
            
        Returns:
            Dictionary with download result
        """
            progress_tracker = ProgressTracker(progress_callback)
        return self.audio_handler.download_audio(
            url, output_dir, audio_format, progress_tracker,
            debug_available_formats_func=self.video_handler.debug_available_formats
        )


# Module-level instance
_downloader_instance = None


def get_downloader():
    """Get or create the global downloader instance"""
    global _downloader_instance
    if _downloader_instance is None:
        _downloader_instance = VideoDownloader()
    return _downloader_instance


# Public API functions that can be called from Kotlin
def download_video(url: str, output_dir: str, quality: str = 'best', progress_callback=None, format_id=None):
    """Public API for downloading video"""
    downloader = get_downloader()
    return downloader.download_video(url, output_dir, quality, progress_callback, format_id)


def download_audio(url: str, output_dir: str, audio_format: str = 'mp3', progress_callback=None):
    """Public API for downloading audio"""
    downloader = get_downloader()
    return downloader.download_audio(url, output_dir, audio_format, progress_callback)


def get_video_info(url: str):
    """Public API for getting video info"""
    downloader = get_downloader()
    return downloader.get_video_info(url)


def cancel_download():
    """Public API for cancelling download"""
    downloader = get_downloader()
    downloader.cancel()


def is_cancelled():
    """Public API for checking if cancelled"""
    downloader = get_downloader()
    return downloader.is_cancelled()


def reset_cancellation():
    """Public API for resetting cancellation flag"""
    downloader = get_downloader()
    downloader.reset_cancellation()
