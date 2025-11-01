"""
Utility functions and classes for yt-dlp video downloader
"""

import sys
import time
from typing import Dict, Any, Optional, Callable

# Debug flag - set by Kotlin side
DEBUG_MODE = True


def debug_print(*args, **kwargs):
    """Print only in debug mode"""
    if DEBUG_MODE:
        print(*args, **kwargs)


def debug_print_stderr(*args, **kwargs):
    """Print to stderr only in debug mode"""
    if DEBUG_MODE:
        print(*args, file=sys.stderr, **kwargs)


class ProgressTracker:
    """Track download progress and report to callback"""
    
    def __init__(self, callback: Optional[Callable] = None):
        self.callback = callback
        self.last_update_time = 0
        self.update_interval = 0.5  # Update every 0.5 seconds
        
    def __call__(self, d: Dict[str, Any]):
        """Called by yt-dlp with progress updates"""
        try:
            # Throttle updates to avoid overwhelming the UI
            current_time = time.time()
            if current_time - self.last_update_time < self.update_interval:
                if d.get('status') != 'finished' and d.get('status') != 'error':
                    return
            
            self.last_update_time = current_time
            
            if self.callback:
                self.callback(d)
            
            # Log progress to stdout for debugging
            status = d.get('status', 'unknown')
            if status == 'downloading':
                downloaded = d.get('downloaded_bytes', 0)
                total = d.get('total_bytes', 0) or d.get('total_bytes_estimate', 0)
                speed = d.get('speed', 0)
                eta = d.get('eta', 0)
                
                if total > 0:
                    percent = (downloaded / total) * 100
                    debug_print(f"[progress] {percent:.1f}% - {downloaded}/{total} bytes - Speed: {speed} - ETA: {eta}s", flush=True)
                else:
                    debug_print(f"[progress] {downloaded} bytes downloaded", flush=True)
                    
            elif status == 'finished':
                filename = d.get('filename', 'unknown')
                debug_print(f"[finished] Downloaded: {filename}", flush=True)
                
            elif status == 'error':
                debug_print_stderr(f"[error] Download error occurred", flush=True)
                
        except Exception as e:
            debug_print_stderr(f"Error in progress callback: {e}", flush=True)


def get_ydl_base_options(debug_mode: bool = False) -> Dict[str, Any]:
    """Get common yt-dlp options"""
    return {
        'quiet': not debug_mode,
        'no_warnings': not debug_mode,
        'verbose': debug_mode,
        'ignoreerrors': True,
        'no_check_certificate': True,
        'prefer_insecure': False,
        'retries': 10,
        'fragment_retries': 10,
        'skip_unavailable_fragments': True,
        'socket_timeout': 30,
        # CRITICAL: Prevents yt-dlp from attempting to merge files with ffmpeg
        'no_direct_merge': True,  # Essential for MediaMuxer-based architecture
        'prefer_ffmpeg': False,
        'writeinfojson': False,
        'writethumbnail': False,
        'writesubtitles': False,
        'writeautomaticsub': False,
        'http_headers': {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': 'en-us,en;q=0.5',
            'Sec-Fetch-Mode': 'navigate'
        }
    }


def create_result_dict(
    success: bool,
    error: Optional[str] = None,
    file_path: Optional[str] = None,
    video_path: Optional[str] = None,
    audio_path: Optional[str] = None,
    separate_av: bool = False,
    needs_extraction: bool = False,
    file_size: int = 0
) -> Dict[str, Any]:
    """Create a standardized result dictionary"""
    return {
        'success': success,
        'error': error,
        'file_path': file_path,
        'video_path': video_path,
        'audio_path': audio_path,
        'separate_av': separate_av,
        'needs_extraction': needs_extraction,
        'file_size': file_size
    }

