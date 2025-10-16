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
        """Extract useful format information"""
        format_list = []
        
        for fmt in formats:
            format_list.append({
                'format_id': fmt.get('format_id', ''),
                'ext': fmt.get('ext', ''),
                'resolution': fmt.get('resolution', 'audio only' if fmt.get('vcodec') == 'none' else 'unknown'),
                'filesize': fmt.get('filesize', 0),
                'vcodec': fmt.get('vcodec', 'none'),
                'acodec': fmt.get('acodec', 'none'),
                'fps': fmt.get('fps', 0),
                'format_note': fmt.get('format_note', ''),
            })
            
        return format_list
        
    def download_video(
        self,
        url: str,
        output_dir: str,
        quality: str = 'best',
        progress_callback: Optional[Callable] = None
    ) -> Dict[str, Any]:
        """
        Download video without requiring ffmpeg
        Downloads video and audio separately if needed
        
        Args:
            url: Video URL
            output_dir: Directory to save the video
            quality: Quality selection (e.g., 'best', '1080p', '720p', 'worst')
            progress_callback: Callback function for progress updates
            
        Returns:
            Dictionary with download result
        """
        try:
            self.cancelled = False
            
            print(f"[info] Starting download: {url}", flush=True)
            print(f"[info] Output directory: {output_dir}", flush=True)
            print(f"[info] Quality: {quality}", flush=True)
            
            # Create progress tracker
            progress_tracker = ProgressTracker(progress_callback)
            
            # First, try to download a pre-merged format (no ffmpeg needed)
            # Many sites provide pre-merged formats
            ydl_opts_merged = {
                'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
                'progress_hooks': [progress_tracker],
                'quiet': False,
                'no_warnings': False,
                # Try to get pre-merged format first
                'format': self._get_merged_format_string(quality),
                'ignoreerrors': False,
                'postprocessors': [],
                'retries': 10,
                'fragment_retries': 10,
                'skip_unavailable_fragments': True,
                'socket_timeout': 30,
                'prefer_ffmpeg': False,
            }
            
            downloaded_files = []
            needs_separate_streams = False
            
            try:
                print(f"[info] Attempting to download pre-merged format", flush=True)
                with yt_dlp.YoutubeDL(ydl_opts_merged) as ydl:
                    info = ydl.extract_info(url, download=True)
                    
                    if info and 'requested_downloads' in info:
                        for download in info['requested_downloads']:
                            filepath = download.get('filepath')
                            if filepath and os.path.exists(filepath):
                                downloaded_files.append(filepath)
                                print(f"[info] Downloaded pre-merged: {filepath}", flush=True)
            except Exception as e:
                # Pre-merged format not available, need to download separate streams
                error_str = str(e).lower()
                if ('requested merging' in error_str or 
                    'ffmpeg' in error_str or 
                    'format is not available' in error_str or
                    'no video formats' in error_str):
                    print(f"[info] Pre-merged format not available ({str(e)}), downloading separate streams", flush=True)
                    needs_separate_streams = True
                else:
                    raise
            
            # If pre-merged failed, download video and audio separately
            if needs_separate_streams or not downloaded_files:
                print(f"[info] Downloading video and audio separately", flush=True)
                
                # Download video stream
                video_opts = {
                    'outtmpl': os.path.join(output_dir, '%(title)s_video.%(ext)s'),
                    'progress_hooks': [progress_tracker],
                    'quiet': False,
                    'no_warnings': False,
                    'format': self._get_video_only_format(quality),
                    'ignoreerrors': False,
                    'postprocessors': [],
                    'retries': 10,
                    'fragment_retries': 10,
                    'skip_unavailable_fragments': True,
                    'socket_timeout': 30,
                }
                
                video_path = None
                with yt_dlp.YoutubeDL(video_opts) as ydl:
                    info = ydl.extract_info(url, download=True)
                    if info and 'requested_downloads' in info:
                        video_path = info['requested_downloads'][0].get('filepath')
                        print(f"[info] Downloaded video: {video_path}", flush=True)
                
                # Download audio stream
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
                with yt_dlp.YoutubeDL(audio_opts) as ydl:
                    info = ydl.extract_info(url, download=True)
                    if info and 'requested_downloads' in info:
                        audio_path = info['requested_downloads'][0].get('filepath')
                        print(f"[info] Downloaded audio: {audio_path}", flush=True)
                
                if video_path and audio_path and os.path.exists(video_path) and os.path.exists(audio_path):
                    # Return separate streams for MediaMuxer to merge
                    return {
                        'success': True,
                        'error': None,
                        'file_path': None,
                        'video_path': video_path,
                        'audio_path': audio_path,
                        'separate_av': True,
                        'file_size': os.path.getsize(video_path) + os.path.getsize(audio_path)
                    }
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
            
            # Pre-merged download succeeded
            if downloaded_files:
                main_file = downloaded_files[0]
                return {
                    'success': True,
                    'error': None,
                    'file_path': main_file,
                    'video_path': None,
                    'audio_path': None,
                    'separate_av': False,
                    'file_size': os.path.getsize(main_file)
                }
            
            return {
                'success': False,
                'error': 'No files were downloaded',
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
            
    def _get_merged_format_string(self, quality: str) -> str:
        """
        Get yt-dlp format string for pre-merged formats (no ffmpeg needed)
        These formats have both video and audio already muxed together
        """
        # For audio-only downloads
        if quality.startswith('audio_'):
            return 'bestaudio/best'
        
        # Quality mapping - try to get pre-merged formats
        # Format syntax: best[height<=X] means best pre-merged format at that height
        quality_map = {
            '2160p': 'best[height<=2160]/bestvideo[height<=2160][ext=mp4]+bestaudio[ext=m4a]/best',
            '1440p': 'best[height<=1440]/bestvideo[height<=1440][ext=mp4]+bestaudio[ext=m4a]/best',
            '1080p': 'best[height<=1080]/bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best',
            '720p': 'best[height<=720]/bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best',
            '480p': 'best[height<=480]/bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best',
            '360p': 'best[height<=360]/bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best',
            'worst': 'worst',
            'best': 'best',
        }
        
        # Default to best pre-merged format
        return quality_map.get(quality, 'best')
    
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
def download_video(url: str, output_dir: str, quality: str = 'best', progress_callback=None):
    """Public API for downloading video"""
    downloader = get_downloader()
    return downloader.download_video(url, output_dir, quality, progress_callback)


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

