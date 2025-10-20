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

# Debug flag - set by Kotlin side
DEBUG_MODE = False

def debug_print(*args, **kwargs):
    """Print only in debug mode"""
    if DEBUG_MODE:
        print(*args, **kwargs)

def debug_print_stderr(*args, **kwargs):
  """Print to stderr only in debug mode"""
  if DEBUG_MODE:
      print(*args, file=sys.stderr, **kwargs)

try:
    import yt_dlp
except ImportError as e:
    debug_print_stderr(f"Failed to import yt_dlp: {e}", file=sys.stderr)
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
                    debug_print(f"[progress] {percent:.1f}% - {downloaded}/{total} bytes - Speed: {speed} - ETA: {eta}s", flush=True)
                else:
                    debug_print(f"[progress] {downloaded} bytes downloaded", flush=True)
                    
            elif status == 'finished':
                filename = d.get('filename', 'unknown')
                debug_print(f"[finished] Downloaded: {filename}", flush=True)
                
            elif status == 'error':
                debug_print_stderr(f"[error] Download error occurred", file=sys.stderr, flush=True)
                
        except Exception as e:
            debug_print_stderr(f"Error in progress callback: {e}", file=sys.stderr, flush=True)


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
    
    def reset_cancellation(self):
        """Reset the cancellation flag for a new download"""
        self.cancelled = False
        
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
            
            debug_print(f"[info] Starting download: {url}", flush=True)
            debug_print(f"[info] Output directory: {output_dir}", flush=True)
            if format_id:
                debug_print(f"[info] Format ID: {format_id}", flush=True)
            else:
                debug_print(f"[info] Quality: {quality}", flush=True)
            
            # Create progress tracker
            progress_tracker = ProgressTracker(progress_callback)
            
            # Determine format string to use
            if format_id:
                # Use specific format ID provided by user
                video_format = format_id
                debug_print(f"[info] Using specific format ID: {format_id}", flush=True)
            else:
                # Use quality-based selection (fallback for backward compatibility)
                video_format = self._get_video_only_format(quality)
                debug_print(f"[info] Using quality-based format: {video_format}", flush=True)
            
            # Download video stream
            debug_print(f"[info] Downloading video stream...", flush=True)
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
                        debug_print(f"[info] Downloaded video: {video_path}", flush=True)
                        debug_print(f"[info] Video has audio: {video_has_audio}", flush=True)
            except Exception as e:
                error_msg = f"Failed to download video: {str(e)}"
                debug_print_stderr(f"[error] {error_msg}", file=sys.stderr, flush=True)
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
                debug_print(f"[info] Video has audio, no separate audio needed", flush=True)
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
            debug_print(f"[info] Downloading audio stream...", flush=True)
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
                        debug_print(f"[info] Downloaded audio: {audio_path}", flush=True)
            except Exception as e:
                error_msg = f"Failed to download audio: {str(e)}"
                debug_print_stderr(f"[error] {error_msg}", file=sys.stderr, flush=True)
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
                debug_print(f"[info] Successfully downloaded video and audio separately", flush=True)
                debug_print(f"[info] Video: {video_path} ({os.path.getsize(video_path)} bytes)", flush=True)
                debug_print(f"[info] Audio: {audio_path} ({os.path.getsize(audio_path)} bytes)", flush=True)
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
                debug_print(f"[info] Returning result: {result}", flush=True)
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
            debug_print_stderr(f"[error] Download failed: {error_msg}", file=sys.stderr, flush=True)
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
            
            debug_print(f"[info] Starting audio download: {url}", flush=True)
            debug_print(f"[info] Output directory: {output_dir}", flush=True)
            debug_print(f"[info] Format: {audio_format}", flush=True)
            
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
            debug_print_stderr(f"[error] Audio download failed: {error_msg}", file=sys.stderr, flush=True)
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


def reset_cancellation():
    """Public API for resetting cancellation flag"""
    downloader = get_downloader()
    downloader.reset_cancellation()


def download_specific_formats(
    self,
    url: str,
    output_dir: str,
    video_format_id: Optional[str] = None,
    audio_format_id: Optional[str] = None,
    progress_callback: Optional[Callable] = None
) -> Dict[str, Any]:
    """
    Download specific video and audio format combinations
    This is called when SmartFormatSelector determines the optimal formats
    """
    try:
        self.cancelled = False

        debug_print(f"[info] Starting specific format download: {url}", flush=True)
        debug_print(f"[info] Video format: {video_format_id}", flush=True)
        debug_print(f"[info] Audio format: {audio_format_id}", flush=True)

        # Create progress tracker
        progress_tracker = ProgressTracker(progress_callback)

        downloaded_files = []
        total_size = 0

        # Download video if specified
        if video_format_id:
            video_result = self._download_single_format(
                url, output_dir, video_format_id, "video", progress_tracker
            )
            if not video_result['success']:
                return video_result
            downloaded_files.append(video_result)
            total_size += video_result.get('file_size', 0)

        # Download audio if specified
        if audio_format_id:
            audio_result = self._download_single_format(
                url, output_dir, audio_format_id, "audio", progress_tracker
            )
            if not audio_result['success']:
                # Clean up video file if audio download fails
                if video_format_id and downloaded_files:
                    try:
                        os.remove(downloaded_files[0]['file_path'])
                    except:
                        pass
                return audio_result
            downloaded_files.append(audio_result)
            total_size += audio_result.get('file_size', 0)

        # Determine result based on what was downloaded
        if len(downloaded_files) == 1:
            # Single file downloaded
            file_info = downloaded_files[0]
            return {
                'success': True,
                'error': None,
                'file_path': file_info['file_path'],
                'video_path': None,
                'audio_path': None,
                'separate_av': False,
                'file_size': file_info['file_size']
            }

        elif len(downloaded_files) == 2:
            # Two files downloaded
            video_file = next((f for f in downloaded_files if f['type'] == 'video'), None)
            audio_file = next((f for f in downloaded_files if f['type'] == 'audio'), None)

            return {
                'success': True,
                'error': None,
                'file_path': None,
                'video_path': video_file['file_path'] if video_file else None,
                'audio_path': audio_file['file_path'] if audio_file else None,
                'separate_av': True,
                'file_size': total_size
            }

        else:
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
        debug_print_stderr(f"[error] Specific format download failed: {error_msg}", file=sys.stderr, flush=True)

        return {
            'success': False,
            'error': error_msg,
            'file_path': None,
            'video_path': None,
            'audio_path': None,
            'separate_av': False,
            'file_size': 0
        }

def _download_single_format(
    self,
    url: str,
    output_dir: str,
    format_id: str,
    file_type: str,  # 'video' or 'audio'
    progress_tracker
) -> Dict[str, Any]:
    """
    Download a single specific format
    """
    try:
        debug_print(f"[info] Downloading {file_type} format {format_id}", flush=True)

        # Configure output template based on file type
        if file_type == 'video':
            outtmpl = os.path.join(output_dir, '%(title)s_video.%(ext)s')
        else:
            outtmpl = os.path.join(output_dir, '%(title)s_audio.%(ext)s')

        ydl_opts = {
            'format': format_id,
            'outtmpl': outtmpl,
            'progress_hooks': [progress_tracker] if progress_tracker else [],
            'quiet': False,
            'no_warnings': False,
            'ignoreerrors': False,
            'postprocessors': [],
            'retries': 10,
            'fragment_retries': 10,
            'skip_unavailable_fragments': True,
            'socket_timeout': 30,
        }

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)

            if info and 'requested_downloads' in info:
                download_info = info['requested_downloads'][0]
                filepath = download_info.get('filepath')

                if filepath and os.path.exists(filepath):
                    file_size = os.path.getsize(filepath)
                    debug_print(f"[info] Downloaded {file_type}: {filepath} ({file_size} bytes)", flush=True)

                    return {
                        'success': True,
                        'file_path': filepath,
                        'file_size': file_size,
                        'type': file_type
                    }

            return {
                'success': False,
                'error': f'Failed to download {file_type} format {format_id}',
                'type': file_type
            }

    except Exception as e:
        error_msg = f"Error downloading {file_type} format {format_id}: {str(e)}"
        debug_print_stderr(f"[error] {error_msg}", file=sys.stderr, flush=True)

        return {
            'success': False,
            'error': error_msg,
            'type': file_type
        }

def download_combined_format(
    self,
    url: str,
    output_dir: str,
    format_id: str,
    progress_callback: Optional[Callable] = None
) -> Dict[str, Any]:
    """
    Download a specific combined format (video + audio in one file)
    """
    try:
        self.cancelled = False

        debug_print(f"[info] Starting combined format download: {url}", flush=True)
        debug_print(f"[info] Format ID: {format_id}", flush=True)

        # Create progress tracker
        progress_tracker = ProgressTracker(progress_callback)

        ydl_opts = {
            'format': format_id,
            'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
            'progress_hooks': [progress_tracker] if progress_tracker else [],
            'quiet': False,
            'no_warnings': False,
            'ignoreerrors': False,
            'postprocessors': [],
            'retries': 10,
            'fragment_retries': 10,
            'skip_unavailable_fragments': True,
            'socket_timeout': 30,
        }

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)

            if info and 'requested_downloads' in info:
                download_info = info['requested_downloads'][0]
                filepath = download_info.get('filepath')

                if filepath and os.path.exists(filepath):
                    file_size = os.path.getsize(filepath)
                    debug_print(f"[info] Downloaded combined format: {filepath} ({file_size} bytes)", flush=True)

                    return {
                        'success': True,
                        'error': None,
                        'file_path': filepath,
                        'video_path': None,
                        'audio_path': None,
                        'separate_av': False,
                        'file_size': file_size
                    }

            return {
                'success': False,
                'error': f'Failed to download combined format {format_id}',
                'file_path': None,
                'video_path': None,
                'audio_path': None,
                'separate_av': False,
                'file_size': 0
            }

    except Exception as e:
        error_msg = str(e)
        debug_print_stderr(f"[error] Combined format download failed: {error_msg}", file=sys.stderr, flush=True)

        return {
            'success': False,
            'error': error_msg,
            'file_path': None,
            'video_path': None,
            'audio_path': None,
            'separate_av': False,
            'file_size': 0
        }

# Public API functions for Kotlin to call
def download_specific_combination(url: str, output_dir: str, video_format_id=None, audio_format_id=None, progress_callback=None):
    """Public API for downloading specific format combinations"""
    downloader = get_downloader()
    return downloader.download_specific_formats(url, output_dir, video_format_id, audio_format_id, progress_callback)

def download_combined(url: str, output_dir: str, format_id: str, progress_callback=None):
    """Public API for downloading combined formats"""
    downloader = get_downloader()
    return downloader.download_combined_format(url, output_dir, format_id, progress_callback)
