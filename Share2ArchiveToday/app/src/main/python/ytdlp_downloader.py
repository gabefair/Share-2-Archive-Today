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
                'quiet': False,  # Enable logging for debugging
                'no_warnings': False,  # Show warnings for debugging
                'extract_flat': False,
                'dump_single_json': True,  # This will print JSON to stdout
            }
            
            print(f"[DEBUG] Getting video info for: {url}", flush=True)
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                
                # Log the full JSON info for debugging
                print(f"[DEBUG] Full yt-dlp JSON output:", flush=True)
                try:
                    import json
                    json_output = json.dumps(info, indent=2, default=str)
                    print(json_output, flush=True)
                except Exception as json_e:
                    print(f"[DEBUG] Could not serialize JSON: {json_e}", flush=True)
                    print(f"[DEBUG] Raw info keys: {list(info.keys()) if info else 'None'}", flush=True)
                
                # Extract and log format information
                formats = info.get('formats', [])
                print(f"[DEBUG] Found {len(formats)} formats:", flush=True)
                for i, fmt in enumerate(formats):
                    print(f"[DEBUG] Format {i}: {fmt.get('format_id', 'unknown')} - {fmt.get('resolution', 'unknown')} - {fmt.get('ext', 'unknown')} - {fmt.get('vcodec', 'none')}/{fmt.get('acodec', 'none')}", flush=True)
                
                return {
                    'title': info.get('title', 'Unknown'),
                    'uploader': info.get('uploader', 'Unknown'),
                    'duration': info.get('duration', 0),
                    'formats': self._extract_format_info(formats),
                    'thumbnail': info.get('thumbnail', ''),
                    'description': info.get('description', ''),
                    'extractor': info.get('extractor', 'unknown'),
                    'extractor_key': info.get('extractor_key', 'unknown'),
                    'webpage_url': info.get('webpage_url', url),
                }
                
        except Exception as e:
            print(f"Error getting video info: {e}", file=sys.stderr, flush=True)
            traceback.print_exc(file=sys.stderr)
            raise
            
    def _extract_format_info(self, formats: list) -> list:
        """Extract useful format information, filtering for video formats"""
        format_list = []
        seen_resolutions = set()
        
        print(f"[DEBUG] Processing {len(formats)} formats for quality selection", flush=True)
        
        for fmt in formats:
            # Get format details
            vcodec = fmt.get('vcodec', 'none')
            acodec = fmt.get('acodec', 'none')
            height = fmt.get('height', 0) or 0  # Handle None values
            width = fmt.get('width', 0) or 0    # Handle None values
            format_id = fmt.get('format_id', '')
            ext = fmt.get('ext', '')
            
            # Determine if format has video/audio
            # Handle both 'none' string and None/null values
            has_video = vcodec not in ('none', None, 'null') and vcodec != ''
            has_audio = acodec not in ('none', None, 'null') and acodec != ''
            
            # Special case: yt-dlp generic extractor often reports null codecs for direct video links
            # If we have a video file extension but null codecs, assume it's a video file
            video_extensions = ['mp4', 'webm', 'mkv', 'avi', 'mov', 'flv', 'm4v', 'mpg', 'mpeg', 'wmv']
            audio_extensions = ['mp3', 'aac', 'm4a', 'wav', 'ogg', 'opus', 'flac']
            
            if ext in video_extensions and not has_video and not has_audio:
                # Assume it's a video file with unknown codecs
                has_video = True
                has_audio = True  # Most video files have audio
                print(f"[DEBUG] Format {format_id}: Assuming video+audio for .{ext} file with null codecs", flush=True)
            elif ext in audio_extensions and not has_audio:
                has_audio = True
                print(f"[DEBUG] Format {format_id}: Assuming audio for .{ext} file", flush=True)
            
            # Skip formats that are neither video nor audio
            if not has_video and not has_audio:
                print(f"[DEBUG] Skipping format {format_id}: no video or audio (vcodec={vcodec}, acodec={acodec})", flush=True)
                continue
            
            # Create resolution string
            if height and width:
                resolution = f"{width}x{height}"
                quality_label = f"{height}p"
            else:
                # For formats without resolution info (generic extractor)
                # Use a generic label based on the format
                resolution = fmt.get('resolution', 'unknown')
                if resolution in ('unknown', None, 'null'):
                    if has_video:
                        quality_label = "Original Quality"
                        # Use a high value for sorting purposes so it appears first
                        height = 9999
                    else:
                        quality_label = "Audio Only"
                        height = 0
                else:
                    quality_label = resolution
            
            # For audio-only formats, use a special label
            if not has_video and has_audio:
                quality_label = "Audio Only"
                resolution = "Audio"
                height = 0
            
            # Skip duplicate resolutions (keep first one which is usually best)
            res_key = (height, has_audio, has_video, format_id)  # Include format_id to avoid skipping same resolution different formats
            if res_key in seen_resolutions:
                print(f"[DEBUG] Skipping duplicate format {format_id}: {quality_label}", flush=True)
                continue
            seen_resolutions.add(res_key)
            
            format_info = {
                'format_id': format_id,
                'ext': ext,
                'resolution': resolution,
                'height': height,
                'quality_label': quality_label,
                'filesize': fmt.get('filesize') or fmt.get('filesize_approx', 0) or 0,
                'vcodec': str(vcodec) if vcodec else 'none',
                'acodec': str(acodec) if acodec else 'none',
                'has_audio': has_audio,
                'has_video': has_video,
                'fps': fmt.get('fps', 0) or 0,
                'format_note': fmt.get('format_note', ''),
                'tbr': fmt.get('tbr', 0) or 0,  # Total bitrate
                'url': fmt.get('url', ''),  # Direct URL for testing
            }
            
            format_list.append(format_info)
            print(f"[DEBUG] Added format {format_id}: {quality_label} ({resolution}) - has_video={has_video}, has_audio={has_audio}", flush=True)
        
        # Sort by height (descending) so best quality is first, audio-only at end
        format_list.sort(key=lambda x: (x['has_video'], x['height']), reverse=True)
        
        print(f"[DEBUG] Final format list has {len(format_list)} formats", flush=True)
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
            
            # Try multiple format strategies for better compatibility
            format_strategies = [
                video_format,  # Try the requested format first
                'best',        # Fallback to best available
                'bestvideo+bestaudio/best',  # Try combined video+audio
                'bestvideo',   # Try video only
                'bestaudio',   # Try audio only as last resort
            ]
            
            last_error = None
            
            for i, strategy in enumerate(format_strategies):
                if self.cancelled:
                    return {
                        'success': False,
                        'error': 'Download cancelled by user',
                        'file_path': None,
                        'video_path': None,
                        'audio_path': None,
                        'separate_av': False,
                        'file_size': 0
                    }
                
                print(f"[info] Trying format strategy {i+1}/{len(format_strategies)}: {strategy}", flush=True)
                
                try:
                    # Configure yt-dlp options
                    ydl_opts = {
                        'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
                        'progress_hooks': [progress_tracker],
                        'quiet': False,
                        'no_warnings': False,
                        'format': strategy,
                        'ignoreerrors': True,  # Always ignore errors as requested
                        'postprocessors': [],
                        'retries': 10,
                        'fragment_retries': 10,
                        'skip_unavailable_fragments': True,
                        'socket_timeout': 30,
                    }
                    
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                        info = ydl.extract_info(url, download=True)
                        
                        if info and 'requested_downloads' in info and len(info['requested_downloads']) > 0:
                            download_info = info['requested_downloads'][0]
                            file_path = download_info.get('filepath')
                            
                            if file_path and os.path.exists(file_path):
                                print(f"[info] Successfully downloaded with strategy {strategy}: {file_path}", flush=True)
                                
                                # Check file extension to determine if it's likely a complete video
                                file_ext = os.path.splitext(file_path)[1].lower()
                                
                                # Check codec information from the downloaded format
                                # Use requested_downloads info which is more reliable after download
                                downloaded_vcodec = download_info.get('vcodec', 'none')
                                downloaded_acodec = download_info.get('acodec', 'none')
                                
                                # For common video container formats, assume they have audio unless proven otherwise
                                # yt-dlp generic extractor often reports null codecs for direct file links
                                container_with_audio = file_ext in ['.mp4', '.mkv', '.avi', '.mov', '.webm', '.flv']
                                
                                # Determine if we have audio based on codec info or container type
                                has_audio = (downloaded_acodec not in ('none', None, 'null')) or container_with_audio
                                has_video = (downloaded_vcodec not in ('none', None, 'null')) or (file_ext not in ['.mp3', '.aac', '.m4a', '.wav', '.ogg'])
                                
                                print(f"[info] File analysis: ext={file_ext}, vcodec={downloaded_vcodec}, acodec={downloaded_acodec}", flush=True)
                                print(f"[info] Determined: has_video={has_video}, has_audio={has_audio}", flush=True)
                                
                                if has_video and has_audio:
                                    # Combined file - we're done
                                    print(f"[info] Downloaded combined video+audio file", flush=True)
                                    return {
                                        'success': True,
                                        'error': None,
                                        'file_path': file_path,
                                        'video_path': None,
                                        'audio_path': None,
                                        'separate_av': False,
                                        'file_size': os.path.getsize(file_path)
                                    }
                                elif has_video and not has_audio:
                                    # Video only - need to get audio separately
                                    print(f"[info] Downloaded video only, getting audio separately", flush=True)
                                    return self._download_separate_audio(url, output_dir, file_path, progress_tracker)
                                elif not has_video and has_audio:
                                    # Audio only - this is what we wanted
                                    print(f"[info] Downloaded audio only", flush=True)
                                    return {
                                        'success': True,
                                        'error': None,
                                        'file_path': file_path,
                                        'video_path': None,
                                        'audio_path': None,
                                        'separate_av': False,
                                        'file_size': os.path.getsize(file_path)
                                    }
                                else:
                                    # Unknown format, but file exists - return as success
                                    print(f"[info] Downloaded file (unknown codec info)", flush=True)
                                    return {
                                        'success': True,
                                        'error': None,
                                        'file_path': file_path,
                                        'video_path': None,
                                        'audio_path': None,
                                        'separate_av': False,
                                        'file_size': os.path.getsize(file_path)
                                    }
                        
                except Exception as e:
                    last_error = str(e)
                    print(f"[warning] Strategy {strategy} failed: {last_error}", flush=True)
                    continue
            
            # All strategies failed
            error_msg = f"All download strategies failed. Last error: {last_error}"
            print(f"[error] {error_msg}", file=sys.stderr, flush=True)
            return {
                'success': False,
                'error': error_msg,
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
    
    def _download_separate_audio(self, url: str, output_dir: str, video_path: str, progress_tracker) -> Dict[str, Any]:
        """Download audio separately when video doesn't have audio"""
        try:
            print(f"[info] Downloading separate audio stream...", flush=True)
            audio_opts = {
                'outtmpl': os.path.join(output_dir, '%(title)s_audio.%(ext)s'),
                'progress_hooks': [progress_tracker],
                'quiet': False,
                'no_warnings': False,
                'format': 'bestaudio',
                'ignoreerrors': True,
                'postprocessors': [],
                'retries': 10,
                'fragment_retries': 10,
                'skip_unavailable_fragments': True,
                'socket_timeout': 30,
            }
            
            with yt_dlp.YoutubeDL(audio_opts) as ydl:
                info = ydl.extract_info(url, download=True)
                if info and 'requested_downloads' in info and len(info['requested_downloads']) > 0:
                    audio_path = info['requested_downloads'][0].get('filepath')
                    
                    if audio_path and os.path.exists(audio_path):
                        print(f"[info] Successfully downloaded separate audio: {audio_path}", flush=True)
                        print(f"[info] Video: {video_path} ({os.path.getsize(video_path)} bytes)", flush=True)
                        print(f"[info] Audio: {audio_path} ({os.path.getsize(audio_path)} bytes)", flush=True)
                        
                        return {
                            'success': True,
                            'error': None,
                            'file_path': None,
                            'video_path': video_path,
                            'audio_path': audio_path,
                            'separate_av': True,
                            'file_size': os.path.getsize(video_path) + os.path.getsize(audio_path)
                        }
            
            return {
                'success': False,
                'error': 'Failed to download separate audio stream',
                'file_path': None,
                'video_path': None,
                'audio_path': None,
                'separate_av': False,
                'file_size': 0
            }
            
        except Exception as e:
            error_msg = f"Failed to download separate audio: {str(e)}"
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


def reset_cancellation():
    """Public API for resetting cancellation flag"""
    downloader = get_downloader()
    downloader.reset_cancellation()

