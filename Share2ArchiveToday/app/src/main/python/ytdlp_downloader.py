"""
Video downloader using yt-dlp
Handles video downloads without requiring ffmpeg by downloading separate streams
"""

import os
import sys
import json
import traceback
from typing import Dict, Any, Optional, Callable
import time

try:
    import yt_dlp
except ImportError as e:
    print(f"Failed to import yt_dlp: {e}", file=sys.stderr)
    raise


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
                    print(f"[progress] {percent:.1f}% - {downloaded}/{total} bytes - Speed: {speed} - ETA: {eta}s", flush=True)
                else:
                    print(f"[progress] {downloaded} bytes downloaded", flush=True)
                    
            elif status == 'finished':
                filename = d.get('filename', 'unknown')
                print(f"[finished] Downloaded: {filename}", flush=True)
                
            elif status == 'error':
                print(f"[error] Download error occurred", file=sys.stderr, flush=True)
                
        except Exception as e:
            print(f"Error in progress callback: {e}", file=sys.stderr, flush=True)


class VideoDownloader:
    """Download videos using yt-dlp without requiring ffmpeg"""
    
    def __init__(self):
        self.cancelled = False
        
    def cancel(self):
        """Cancel the current download"""
        self.cancelled = True
        
    def is_cancelled(self):
        """Check if download is cancelled"""
        return self.cancelled
        
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
                'quiet': True,
                'no_warnings': True,
                'extract_flat': False,
            }
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                
                return {
                    'title': info.get('title', 'Unknown'),
                    'uploader': info.get('uploader', 'Unknown'),
                    'duration': info.get('duration', 0),
                    'formats': self._extract_format_info(info.get('formats', [])),
                    'thumbnail': info.get('thumbnail', ''),
                    'description': info.get('description', ''),
                }
                
        except Exception as e:
            print(f"Error getting video info: {e}", file=sys.stderr, flush=True)
            traceback.print_exc(file=sys.stderr)
            raise
            
    def _extract_format_info(self, formats: list) -> list:
        """Extract useful format information, filtering for video formats"""
        format_list = []
        seen_resolutions = set()
        
        for fmt in formats:
            # Only include formats with video (skip audio-only)
            vcodec = fmt.get('vcodec', 'none')
            if vcodec == 'none':
                continue
            
            # Get format details
            height = fmt.get('height', 0)
            width = fmt.get('width', 0)
            acodec = fmt.get('acodec', 'none')
            has_audio = acodec != 'none'
            
            # Create resolution string
            if height and width:
                resolution = f"{width}x{height}"
                quality_label = f"{height}p"
            else:
                resolution = fmt.get('resolution', 'unknown')
                quality_label = resolution
            
            # Skip duplicate resolutions (keep first one which is usually best)
            res_key = (height, has_audio)
            if res_key in seen_resolutions:
                continue
            seen_resolutions.add(res_key)
            
            format_list.append({
                'format_id': fmt.get('format_id', ''),
                'ext': fmt.get('ext', ''),
                'resolution': resolution,
                'height': height,
                'quality_label': quality_label,
                'filesize': fmt.get('filesize') or fmt.get('filesize_approx', 0),
                'vcodec': vcodec,
                'acodec': acodec,
                'has_audio': has_audio,
                'fps': fmt.get('fps', 0),
                'format_note': fmt.get('format_note', ''),
                'tbr': fmt.get('tbr', 0),  # Total bitrate
            })
        
        # Sort by height (descending) so best quality is first
        format_list.sort(key=lambda x: x['height'], reverse=True)
            
        return format_list
        
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
        Downloads video and audio separately if needed
        
        Args:
            url: Video URL
            output_dir: Directory to save the video
            quality: Quality selection (e.g., 'best', '1080p', '720p', 'worst') - used if format_id not specified
            progress_callback: Callback function for progress updates
            format_id: Specific format ID to download (from get_video_info). If provided, overrides quality.
            
        Returns:
            Dictionary with download result
        """
        try:
            self.cancelled = False
            
            print(f"[info] Starting download: {url}", flush=True)
            print(f"[info] Output directory: {output_dir}", flush=True)
            if format_id:
                print(f"[info] Format ID: {format_id}", flush=True)
            else:
                print(f"[info] Quality: {quality}", flush=True)
            
            # Create progress tracker
            progress_tracker = ProgressTracker(progress_callback)
            
            # Determine format string to use
            if format_id:
                # Use specific format ID provided by user
                video_format = format_id
                print(f"[info] Using specific format ID: {format_id}", flush=True)
            else:
                # Use quality-based selection (fallback for backward compatibility)
                video_format = self._get_video_only_format(quality)
                print(f"[info] Using quality-based format: {video_format}", flush=True)
            
            # Download video stream
            print(f"[info] Downloading video stream...", flush=True)
            video_opts = {
                'outtmpl': os.path.join(output_dir, '%(title)s_video.%(ext)s'),
                'progress_hooks': [progress_tracker],
                'quiet': False,
                'no_warnings': False,
                'format': video_format,
                'ignoreerrors': False,
                'postprocessors': [],
                'retries': 10,
                'fragment_retries': 10,
                'skip_unavailable_fragments': True,
                'socket_timeout': 30,
            }
            
            video_path = None
            video_has_audio = False
            try:
                with yt_dlp.YoutubeDL(video_opts) as ydl:
                    info = ydl.extract_info(url, download=True)
                    if info and 'requested_downloads' in info:
                        video_path = info['requested_downloads'][0].get('filepath')
                        # Check if video has audio
                        acodec = info.get('acodec', 'none')
                        video_has_audio = acodec not in ('none', None)
                        print(f"[info] Downloaded video: {video_path}", flush=True)
                        print(f"[info] Video has audio: {video_has_audio}", flush=True)
            except Exception as e:
                error_msg = f"Failed to download video: {str(e)}"
                print(f"[error] {error_msg}", file=sys.stderr, flush=True)
                traceback.print_exc(file=sys.stderr)
                return {
                    'success': False,
                    'error': error_msg,
                    'file_path': None,
                    'video_path': None,
                    'audio_path': None,
                    'separate_av': False,
                    'file_size': 0
                }
            
            # If video already has audio, we're done
            if video_has_audio and video_path and os.path.exists(video_path):
                print(f"[info] Video has audio, no separate audio needed", flush=True)
                return {
                    'success': True,
                    'error': None,
                    'file_path': video_path,
                    'video_path': None,
                    'audio_path': None,
                    'separate_av': False,
                    'file_size': os.path.getsize(video_path)
                }
            
            # Download audio stream separately
            print(f"[info] Downloading audio stream...", flush=True)
            audio_opts = {
                'outtmpl': os.path.join(output_dir, '%(title)s_audio.%(ext)s'),
                'progress_hooks': [progress_tracker],
                'quiet': False,
                'no_warnings': False,
                'format': 'bestaudio',
                'ignoreerrors': False,
                'postprocessors': [],
                'retries': 10,
                'fragment_retries': 10,
                'skip_unavailable_fragments': True,
                'socket_timeout': 30,
            }
            
            audio_path = None
            try:
                with yt_dlp.YoutubeDL(audio_opts) as ydl:
                    info = ydl.extract_info(url, download=True)
                    if info and 'requested_downloads' in info:
                        audio_path = info['requested_downloads'][0].get('filepath')
                        print(f"[info] Downloaded audio: {audio_path}", flush=True)
            except Exception as e:
                error_msg = f"Failed to download audio: {str(e)}"
                print(f"[error] {error_msg}", file=sys.stderr, flush=True)
                traceback.print_exc(file=sys.stderr)
                return {
                    'success': False,
                    'error': error_msg,
                    'file_path': None,
                    'video_path': None,
                    'audio_path': None,
                    'separate_av': False,
                    'file_size': 0
                }
            
            # Verify both files exist
            if video_path and audio_path and os.path.exists(video_path) and os.path.exists(audio_path):
                print(f"[info] Successfully downloaded video and audio separately", flush=True)
                print(f"[info] Video: {video_path} ({os.path.getsize(video_path)} bytes)", flush=True)
                print(f"[info] Audio: {audio_path} ({os.path.getsize(audio_path)} bytes)", flush=True)
                # Return separate streams for MediaMuxer to merge
                result = {
                    'success': True,
                    'error': None,
                    'file_path': None,
                    'video_path': video_path,
                    'audio_path': audio_path,
                    'separate_av': True,
                    'file_size': os.path.getsize(video_path) + os.path.getsize(audio_path)
                }
                print(f"[info] Returning result: {result}", flush=True)
                return result
            else:
                return {
                    'success': False,
                    'error': 'Failed to download video or audio stream',
                    'file_path': None,
                    'video_path': None,
                    'audio_path': None,
                    'separate_av': False,
                    'file_size': 0
                }
                
        except Exception as e:
            error_msg = str(e)
            print(f"[error] Download failed: {error_msg}", file=sys.stderr, flush=True)
            traceback.print_exc(file=sys.stderr)
            
            return {
                'success': False,
                'error': error_msg,
                'file_path': None,
                'video_path': None,
                'audio_path': None,
                'separate_av': False,
                'file_size': 0
            }
    
    def _get_video_only_format(self, quality: str) -> str:
        """
        Get yt-dlp format string for video-only stream
        Used when downloading video and audio separately
        """
        # Quality mapping for video-only
        quality_map = {
            '2160p': 'bestvideo[height<=2160]',
            '1440p': 'bestvideo[height<=1440]',
            '1080p': 'bestvideo[height<=1080]',
            '720p': 'bestvideo[height<=720]',
            '480p': 'bestvideo[height<=480]',
            '360p': 'bestvideo[height<=360]',
            'worst': 'worstvideo',
            'best': 'bestvideo',
        }
        
        # Default to best video
        return quality_map.get(quality, 'bestvideo')
        
    def download_audio(
        self,
        url: str,
        output_dir: str,
        audio_format: str = 'mp3',
        progress_callback: Optional[Callable] = None
    ) -> Dict[str, Any]:
        """
        Download audio only
        
        Args:
            url: Video URL
            output_dir: Directory to save the audio
            audio_format: Audio format (mp3, aac, etc.)
            progress_callback: Callback function for progress updates
            
        Returns:
            Dictionary with download result
        """
        try:
            self.cancelled = False
            
            print(f"[info] Starting audio download: {url}", flush=True)
            print(f"[info] Output directory: {output_dir}", flush=True)
            print(f"[info] Format: {audio_format}", flush=True)
            
            # Create progress tracker
            progress_tracker = ProgressTracker(progress_callback)
            
            # Configure yt-dlp options for audio
            ydl_opts = {
                'format': 'bestaudio/best',
                'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
                'progress_hooks': [progress_tracker],
                'quiet': False,
                'no_warnings': False,
                'postprocessors': [],  # No post-processing (no ffmpeg)
                # Retry settings
                'retries': 10,
                'fragment_retries': 10,
                'skip_unavailable_fragments': True,
                # Network settings  
                'socket_timeout': 30,
            }
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=True)
                
                if info:
                    # Get the downloaded file path
                    if 'requested_downloads' in info:
                        filepath = info['requested_downloads'][0].get('filepath')
                    else:
                        title = info.get('title', 'audio')
                        ext = info.get('ext', audio_format)
                        filepath = os.path.join(output_dir, f"{title}.{ext}")
                    
                    if filepath and os.path.exists(filepath):
                        return {
                            'success': True,
                            'error': None,
                            'file_path': filepath,
                            'video_path': None,
                            'audio_path': None,
                            'separate_av': False,
                            'file_size': os.path.getsize(filepath)
                        }
            
            return {
                'success': False,
                'error': 'Audio file was not downloaded',
                'file_path': None,
                'video_path': None,
                'audio_path': None,
                'separate_av': False,
                'file_size': 0
            }
            
        except Exception as e:
            error_msg = str(e)
            print(f"[error] Audio download failed: {error_msg}", file=sys.stderr, flush=True)
            traceback.print_exc(file=sys.stderr)
            
            return {
                'success': False,
                'error': error_msg,
                'file_path': None,
                'video_path': None,
                'audio_path': None,
                'separate_av': False,
                'file_size': 0
            }


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

