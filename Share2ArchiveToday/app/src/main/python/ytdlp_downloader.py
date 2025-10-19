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
    debug_print_stderr(f"Failed to import yt_dlp: {e}")
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
                debug_print_stderr(f"[error] Download error occurred", flush=True)
                
        except Exception as e:
            debug_print_stderr(f"Error in progress callback: {e}", flush=True)


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
                'quiet': not DEBUG_MODE,  # Enable logging only in debug mode
                'no_warnings': not DEBUG_MODE,  # Show warnings only in debug mode
                'extract_flat': False,
                'dump_single_json': DEBUG_MODE,  # Print JSON only in debug mode
                'ignoreerrors': True,  # Don't fail on individual extractor errors
                'no_check_certificate': True,  # Skip SSL certificate verification
                'prefer_insecure': False,  # Prefer HTTPS when available
                # Reddit-specific options
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
                
                # Check if info extraction was successful
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
                
                # Log the full JSON info for debugging
                debug_print(f"[DEBUG] Full yt-dlp JSON output:", flush=True)
                try:
                    import json
                    json_output = json.dumps(info, indent=2, default=str)
                    debug_print(json_output, flush=True)
                except Exception as json_e:
                    debug_print(f"[DEBUG] Could not serialize JSON: {json_e}", flush=True)
                    debug_print(f"[DEBUG] Raw info keys: {list(info.keys()) if info else 'None'}", flush=True)
                
                # Extract and log format information
                formats = info.get('formats', [])
                debug_print(f"[DEBUG] Found {len(formats)} formats:", flush=True)
                for i, fmt in enumerate(formats):
                    debug_print(f"[DEBUG] Format {i}: {fmt.get('format_id', 'unknown')} - {fmt.get('resolution', 'unknown')} - {fmt.get('ext', 'unknown')} - {fmt.get('vcodec', 'none')}/{fmt.get('acodec', 'none')}", flush=True)
                
                extracted_formats = self._extract_format_info(formats)
                debug_print(f"[DEBUG] Extracted formats for Kotlin: {len(extracted_formats)} formats", flush=True)
                for i, fmt in enumerate(extracted_formats):
                    debug_print(f"[DEBUG] Extracted format {i}: {fmt.get('format_id')} - {fmt.get('quality_label')} - has_video={fmt.get('has_video')}, has_audio={fmt.get('has_audio')}", flush=True)
                
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
                
                debug_print(f"[DEBUG] Returning video info with {len(result.get('formats', []))} formats", flush=True)
                return result
                
        except Exception as e:
            error_msg = str(e)
            debug_print_stderr(f"Error getting video info: {error_msg}", flush=True)
            if DEBUG_MODE:
                traceback.print_exc(file=sys.stderr)
            
            # Return a structured error response instead of raising
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
            
    def _extract_format_info(self, formats: list) -> list:
        """Extract useful format information, intelligently pairing DASH video/audio streams"""
        format_list = []
        seen_resolutions = set()
        
        debug_print(f"[DEBUG] Processing {len(formats)} formats for quality selection", flush=True)
        
        # First pass: identify DASH audio stream and video-only streams
        dash_audio_format = None
        video_only_formats = []
        complete_formats = []
        
        for fmt in formats:
            vcodec = fmt.get('vcodec', 'none')
            acodec = fmt.get('acodec', 'none')
            format_note = fmt.get('format_note', '')
            
            # Check for DASH audio stream
            if (acodec not in ('none', None, 'null') and acodec != '' and 
                vcodec in ('none', None, 'null') and 
                ('dash' in format_note.lower() or 'dash' in fmt.get('format_id', '').lower())):
                dash_audio_format = fmt
                debug_print(f"[DEBUG] Found DASH audio stream: {fmt.get('format_id')}", flush=True)
            
            # Check for video-only streams (DASH or otherwise)
            elif (vcodec not in ('none', None, 'null') and vcodec != '' and 
                  acodec in ('none', None, 'null')):
                video_only_formats.append(fmt)
                debug_print(f"[DEBUG] Found video-only stream: {fmt.get('format_id')} - {fmt.get('format_note', '')}", flush=True)
            
            # Complete formats (have both video and audio)
            elif (vcodec not in ('none', None, 'null') and vcodec != '' and 
                  acodec not in ('none', None, 'null') and acodec != ''):
                complete_formats.append(fmt)
                debug_print(f"[DEBUG] Found complete format: {fmt.get('format_id')}", flush=True)
        
        # Process complete formats first (these are ready to use)
        for fmt in complete_formats:
            format_info = self._process_format(fmt)
            if format_info:
                format_list.append(format_info)
        
        # Process video-only formats by pairing them with DASH audio
        if dash_audio_format and video_only_formats:
            debug_print(f"[DEBUG] Pairing {len(video_only_formats)} video-only streams with DASH audio", flush=True)
            
            # Group video-only formats by resolution to avoid duplicates
            resolution_groups = {}
            for fmt in video_only_formats:
                height = fmt.get('height', 0) or 0
                if height not in resolution_groups:
                    resolution_groups[height] = []
                resolution_groups[height].append(fmt)
            
            # For each resolution, pick the best bitrate and pair with audio
            for height in sorted(resolution_groups.keys(), reverse=True):
                video_formats = resolution_groups[height]
                # Sort by bitrate (descending) to get best quality first
                video_formats.sort(key=lambda x: x.get('tbr', 0) or 0, reverse=True)
                
                # Take only the best bitrate for each resolution
                best_video = video_formats[0]
                
                # Create a paired format
                paired_format = {
                    'format_id': f"{best_video.get('format_id')}+{dash_audio_format.get('format_id')}",
                    'video_format_id': best_video.get('format_id'),
                    'audio_format_id': dash_audio_format.get('format_id'),
                    'ext': best_video.get('ext', 'mp4'),
                    'resolution': best_video.get('resolution', ''),
                    'height': height,
                    'width': best_video.get('width', 0) or 0,
                    'quality_label': f"{height}p",
                    'filesize': (best_video.get('filesize') or best_video.get('filesize_approx', 0) or 0) + 
                               (dash_audio_format.get('filesize') or dash_audio_format.get('filesize_approx', 0) or 0),
                    'vcodec': best_video.get('vcodec', 'none'),
                    'acodec': dash_audio_format.get('acodec', 'none'),
                    'has_audio': True,
                    'has_video': True,
                    'fps': best_video.get('fps', 0) or 0,
                    'format_note': f"DASH {height}p (Video+Audio)",
                    'tbr': (best_video.get('tbr', 0) or 0) + (dash_audio_format.get('tbr', 0) or 0),
                    'url': best_video.get('url', ''),
                    'is_dash_paired': True,  # Flag to indicate this is a paired format
                }
                
                format_list.append(paired_format)
                debug_print(f"[DEBUG] Created paired format: {height}p (Video+Audio) - {paired_format['format_id']}", flush=True)
        
        # Process remaining formats (audio-only, etc.)
        for fmt in formats:
            if fmt not in complete_formats and fmt not in video_only_formats and fmt != dash_audio_format:
                format_info = self._process_format(fmt)
                if format_info:
                    format_list.append(format_info)
        
        # Sort by height (descending) so best quality is first, audio-only at end
        format_list.sort(key=lambda x: (x['has_video'], x['height']), reverse=True)
        
        debug_print(f"[DEBUG] Final format list has {len(format_list)} formats", flush=True)
        return format_list
    
    def _process_format(self, fmt: dict) -> dict:
        """Process a single format and return format info"""
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
            debug_print(f"[DEBUG] Format {format_id}: Assuming video+audio for .{ext} file with null codecs", flush=True)
        elif ext in audio_extensions and not has_audio:
            has_audio = True
            debug_print(f"[DEBUG] Format {format_id}: Assuming audio for .{ext} file", flush=True)
        
        # Skip formats that are neither video nor audio
        if not has_video and not has_audio:
            debug_print(f"[DEBUG] Skipping format {format_id}: no video or audio (vcodec={vcodec}, acodec={acodec})", flush=True)
            return None
        
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
        
        debug_print(f"[DEBUG] Added format {format_id}: {quality_label} ({resolution}) - has_video={has_video}, has_audio={has_audio}", flush=True)
        return format_info
        
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
            quality: Quality selection (e.g., 'best', '1080p', '720p', 'worst') - used for quality-based selection
            progress_callback: Callback function for progress updates
            format_id: DEPRECATED - Specific format ID to download. Now ignored for better compatibility.
            
        Returns:
            Dictionary with download result
        """
        try:
            self.cancelled = False
            
            debug_print(f"[info] Starting download: {url}", flush=True)
            debug_print(f"[info] Output directory: {output_dir}", flush=True)
            debug_print(f"[info] Quality preference: {quality}", flush=True)
            if format_id:
                debug_print(f"[info] Note: format_id '{format_id}' is deprecated, using quality-based selection instead", flush=True)
            debug_print(f"[info] Using quality-based selection for better compatibility", flush=True)
            
            # Create progress tracker
            progress_tracker = ProgressTracker(progress_callback)
            
            # Convert quality preference to yt-dlp format selector
            # This approach is more robust than using specific format IDs
            video_format = self._get_quality_based_format(quality)
            debug_print(f"[info] Using quality-based format selector: {video_format}", flush=True)
            
            # Try multiple format strategies for better compatibility
            # Start with quality-based selection, then fallback to more general options
            format_strategies = [
                video_format,  # Try the quality-based format first
                'best',        # Fallback to best available
                'bestvideo',   # Try video only
                #'bestaudio',   # audio only is a future feature
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
                
                debug_print(f"[info] Trying format strategy {i+1}/{len(format_strategies)}: {strategy}", flush=True)
                
                try:
                    # Configure yt-dlp options for Android compatibility
                    ydl_opts = {
                        'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
                        'progress_hooks': [progress_tracker],
                        'quiet': not DEBUG_MODE,  # Quiet in release builds
                        'no_warnings': not DEBUG_MODE,  # No warnings in release builds
                        'verbose': DEBUG_MODE,  # Verbose only in debug mode
                        'format': strategy,
                        'ignoreerrors': True,  # Always ignore errors as requested
                        'postprocessors': [],
                        'retries': 10,
                        'fragment_retries': 10,
                        'skip_unavailable_fragments': True,
                        'socket_timeout': 30,
                        # CRITICAL: Don't let yt-dlp merge - we handle it with MediaMuxer
                        'no-direct-merge': True,  # Don't merge video/audio - we handle that with MediaMuxer
                        'prefer_ffmpeg': False,  # Don't use ffmpeg since we don't have it
                        'writeinfojson': False,  # Don't write metadata files
                        'writethumbnail': False,  # Don't download thumbnails
                        'writesubtitles': False,  # Don't download subtitles
                        'writeautomaticsub': False,  # Don't download auto-generated subtitles
                        # Container format preferences for Android
                        'format_sort': ['res', 'ext:mp4:m4a', 'proto'],  # Prefer MP4/M4A containers
                        'format_sort_force': True,  # Force format sorting
                        # Add HTTP headers for better compatibility
                        'http_headers': {
                            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36',
                            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
                            'Accept-Language': 'en-us,en;q=0.5',
                            'Sec-Fetch-Mode': 'navigate'
                        }
                    }
                    
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                        # First, let's check what formats are available
                        debug_print(f"[info] Checking available formats for strategy: {strategy}", flush=True)
                        try:
                            info_check = ydl.extract_info(url, download=False)
                            if info_check and 'formats' in info_check:
                                formats = info_check.get('formats', [])
                                debug_print(f"[info] Available formats for {strategy}:", flush=True)
                                for fmt in formats:
                                    debug_print(f"[info]   {fmt.get('format_id', 'unknown')}: {fmt.get('resolution', 'unknown')} - {fmt.get('ext', 'unknown')} - {fmt.get('vcodec', 'none')}/{fmt.get('acodec', 'none')}", flush=True)
                        except Exception as e:
                            debug_print(f"[warning] Could not check formats: {e}", flush=True)
                        
                        info = ydl.extract_info(url, download=True)
                        
                        if info and 'requested_downloads' in info and len(info['requested_downloads']) > 0:
                            download_info = info['requested_downloads'][0]
                            file_path = download_info.get('filepath')
                            
                            if file_path and os.path.exists(file_path):
                                debug_print(f"[info] Successfully downloaded with strategy {strategy}: {file_path}", flush=True)
                                
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
                                
                                debug_print(f"[info] File analysis: ext={file_ext}, vcodec={downloaded_vcodec}, acodec={downloaded_acodec}", flush=True)
                                debug_print(f"[info] Determined: has_video={has_video}, has_audio={has_audio}", flush=True)
                                
                                if has_video and has_audio:
                                    # Combined file - we're done
                                    debug_print(f"[info] Downloaded combined video+audio file", flush=True)
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
                                    debug_print(f"[info] Downloaded video only, getting audio separately", flush=True)
                                    return self._download_separate_audio(url, output_dir, file_path, progress_tracker)
                                elif not has_video and has_audio:
                                    # Audio only - this is what we wanted
                                    debug_print(f"[info] Downloaded audio only", flush=True)
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
                                    debug_print(f"[info] Downloaded file (unknown codec info)", flush=True)
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
                    debug_print(f"[warning] Strategy {strategy} failed: {last_error}", flush=True)
                    continue
            
            # All strategies failed
            error_msg = f"All download strategies failed. Last error: {last_error}"
            debug_print_stderr(f"[error] {error_msg}", flush=True)
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
            debug_print_stderr(f"[error] Download failed: {error_msg}", flush=True)
            if DEBUG_MODE:
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
            debug_print(f"[info] Downloading separate audio stream...", flush=True)
            
            # Try multiple audio format strategies for better compatibility
            audio_format_strategies = [
                'bestaudio',  # Most general selector
                'bestaudio[ext=m4a]/bestaudio[ext=aac]/bestaudio[ext=mp3]/bestaudio',  # Combined with fallback
                'bestaudio[protocol*=dash]/bestaudio',  # DASH with fallback
                'bestaudio[protocol*=m3u8]/bestaudio',  # HLS with fallback
                'bestaudio[ext=m4a]/bestaudio',  # M4A with fallback
                'bestaudio[ext=aac]/bestaudio',  # AAC with fallback
                'bestaudio[ext=mp3]/bestaudio',  # MP3 with fallback
            ]
            
            last_error = None
            
            for i, audio_format in enumerate(audio_format_strategies):
                if self.cancelled:
                    return self._cancelled_result()
                
                try:
                    debug_print(f"[info] Trying audio format strategy {i+1}/{len(audio_format_strategies)}: {audio_format}", flush=True)
                    
                    audio_opts = {
                        'outtmpl': os.path.join(output_dir, '%(title)s_audio.%(ext)s'),
                        'progress_hooks': [progress_tracker],
                        'quiet': not DEBUG_MODE,  # Quiet in release builds
                        'no_warnings': not DEBUG_MODE,  # No warnings in release builds
                        'verbose': DEBUG_MODE,  # Verbose only in debug mode
                        'format': audio_format,
                        'ignoreerrors': True,
                        'postprocessors': [],
                        'retries': 10,
                        'fragment_retries': 10,
                        'skip_unavailable_fragments': True,
                        'socket_timeout': 30,
                        'no-direct-merge': True,  # Don't merge video/audio - we handle that with MediaMuxer
                    }
                    
                    with yt_dlp.YoutubeDL(audio_opts) as ydl:
                        info = ydl.extract_info(url, download=True)
                        if info and 'requested_downloads' in info and len(info['requested_downloads']) > 0:
                            audio_path = info['requested_downloads'][0].get('filepath')
                            
                            if audio_path and os.path.exists(audio_path):
                                debug_print(f"[info] Successfully downloaded separate audio with strategy {audio_format}: {audio_path}", flush=True)
                                debug_print(f"[info] Video: {video_path} ({os.path.getsize(video_path)} bytes)", flush=True)
                                debug_print(f"[info] Audio: {audio_path} ({os.path.getsize(audio_path)} bytes)", flush=True)
                                
                                return {
                                    'success': True,
                                    'error': None,
                                    'file_path': None,
                                    'video_path': video_path,
                                    'audio_path': audio_path,
                                    'separate_av': True,
                                    'file_size': os.path.getsize(video_path) + os.path.getsize(audio_path)
                                }
                except Exception as e:
                    last_error = str(e)
                    debug_print(f"[warning] Audio format strategy {audio_format} failed: {last_error}", flush=True)
                    continue
            
            # All strategies failed
            error_msg = f"Failed to download separate audio stream. All strategies failed. Last error: {last_error}"
            debug_print_stderr(f"[error] {error_msg}", flush=True)
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
            error_msg = f"Failed to download separate audio: {str(e)}"
            debug_print_stderr(f"[error] {error_msg}", flush=True)
            if DEBUG_MODE:
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
    
    def _get_quality_based_format(self, quality: str) -> str:
        """
        Get yt-dlp format string based on quality preference
        Uses intelligent format selection that works with most video platforms
        """
        # Map quality preferences to robust yt-dlp format selectors
        # For DASH streams, we need to handle video+audio separately
        quality_map = {
            # Don't have yt-dlp request merged formats. We will handle merging in Kotlin.
            '2160p': 'best[height<=2160]',
            '1440p': 'best[height<=1440]', 
            '1080p': 'best[height<=1080]',
            '720p': 'best[height<=720]',
            '480p': 'best[height<=480]',
            '360p': 'best[height<=360]',
            
            # Special cases
            'worst': 'worst',
            'best': 'best',
            'audio_mp3': 'bestaudio[ext=mp3]/bestaudio',
            'audio_aac': 'bestaudio[ext=m4a]/bestaudio[ext=aac]/bestaudio',
        }
        
        # Default to best available
        return quality_map.get(quality, 'best')
    
    def _get_video_only_format(self, quality: str) -> str:
        """
        Get yt-dlp format string for video-only stream
        Used when downloading video and audio separately
        DEPRECATED: Use _get_quality_based_format instead
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
        Download audio only with fallback strategies:
        1. Try audio-only formats first
        2. If not available, try DASH formats (which split audio/video)
        3. If still not available, download video+audio and signal extraction needed
        
        Args:
            url: Video URL
            output_dir: Directory to save the audio
            audio_format: Audio format (mp3, aac, etc.)
            progress_callback: Callback function for progress updates
            
        Returns:
            Dictionary with download result. If 'needs_extraction' is True,
            the caller should extract audio from the video file.
        """
        try:
            self.cancelled = False
            
            debug_print(f"[info] Starting audio download: {url}", flush=True)
            debug_print(f"[info] Output directory: {output_dir}", flush=True)
            debug_print(f"[info] Format: {audio_format}", flush=True)
            
            # Debug: List all available formats for troubleshooting
            if DEBUG_MODE:
                self._debug_available_formats(url)
            
            # Create progress tracker
            progress_tracker = ProgressTracker(progress_callback)
            
            # First, check what formats are available
            debug_print(f"[info] Checking available formats...", flush=True)
            try:
                with yt_dlp.YoutubeDL({'quiet': True}) as ydl:
                    info = ydl.extract_info(url, download=False)
                    formats = info.get('formats', [])
                    
                    # Check for audio-only formats
                    has_audio_only = False
                    has_dash_audio = False
                    
                    for fmt in formats:
                        vcodec = fmt.get('vcodec', 'none')
                        acodec = fmt.get('acodec', 'none')
                        has_video = vcodec not in ('none', None, 'null') and vcodec != ''
                        has_audio = acodec not in ('none', None, 'null') and acodec != ''
                        
                        if has_audio and not has_video:
                            has_audio_only = True
                            debug_print(f"[info] Found audio-only format: {fmt.get('format_id')}", flush=True)
                            break
                        elif has_audio and fmt.get('protocol') in ('http_dash_segments', 'm3u8_native', 'm3u8'):
                            has_dash_audio = True
                            debug_print(f"[info] Found DASH/HLS format with audio: {fmt.get('format_id')}", flush=True)
                    
                    if has_audio_only:
                        debug_print(f"[info] Strategy: Direct audio-only download", flush=True)
                    elif has_dash_audio:
                        debug_print(f"[info] Strategy: DASH/HLS audio extraction", flush=True)
                    else:
                        debug_print(f"[info] Strategy: Download video+audio, extraction needed", flush=True)
                        
            except Exception as e:
                debug_print(f"[warning] Could not check formats, will try all strategies: {e}", flush=True)
                has_audio_only = None  # Unknown, will try all strategies
            
            # Strategy 1: Try audio-only formats with better format selection
            # Prioritize formats that are more likely to be properly containerized
            # Use more flexible selectors that work better with DASH streams
            audio_only_formats = [
                'bestaudio',  # Start with the most general selector
                'bestaudio[ext=m4a]/bestaudio[ext=aac]/bestaudio[ext=mp3]/bestaudio',  # Combined selector with fallback
                'bestaudio[ext=m4a]/bestaudio',  # M4A with fallback
                'bestaudio[ext=aac]/bestaudio',   # AAC with fallback
                'bestaudio[ext=mp3]/bestaudio',  # MP3 with fallback
                'bestaudio[ext=opus]/bestaudio',  # Opus with fallback
                'bestaudio[ext=ogg]/bestaudio',   # OGG with fallback
                'bestaudio[protocol*=dash]/bestaudio',  # DASH audio with fallback
                'bestaudio[protocol*=m3u8]/bestaudio',  # HLS audio with fallback
            ]
            
            for audio_fmt in audio_only_formats:
                if self.cancelled:
                    return self._cancelled_result()
                
                try:
                    debug_print(f"[info] Trying audio format: {audio_fmt}", flush=True)
                    ydl_opts = {
                        'format': audio_fmt,
                        'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
                        'progress_hooks': [progress_tracker],
                        'quiet': not DEBUG_MODE,  # Quiet in release builds
                        'no_warnings': not DEBUG_MODE,  # No warnings in release builds
                        'verbose': DEBUG_MODE,  # Verbose only in debug mode
                        'postprocessors': [],
                        'retries': 10,
                        'fragment_retries': 10,
                        'skip_unavailable_fragments': True,
                        'socket_timeout': 30,
                        'ignoreerrors': True,
                        # CRITICAL: Don't let yt-dlp merge - we handle it with MediaMuxer
                        'no-direct-merge': True,  # Don't merge video/audio - we handle that with MediaMuxer
                        'prefer_ffmpeg': False,  # Don't use ffmpeg since we don't have it
                        'prefer_insecure': False,
                        'http_chunk_size': 10485760,  # 10MB chunks for better streaming
                        'concurrent_fragment_downloads': 1,  # Single fragment to avoid corruption
                        'keep_fragments': False,
                        'writeinfojson': False,
                        'writethumbnail': False,
                        'writesubtitles': False,
                        'writeautomaticsub': False,
                        # Container format preferences for Android audio
                        'format_sort': ['ext:m4a:mp3:aac', 'abr', 'proto'],  # Prefer M4A/MP3/AAC containers
                        'format_sort_force': True,  # Force format sorting
                    }
                    
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                        info = ydl.extract_info(url, download=True)
                        
                        if info and 'requested_downloads' in info and len(info['requested_downloads']) > 0:
                            filepath = info['requested_downloads'][0].get('filepath')
                            
                            if filepath and os.path.exists(filepath):
                                debug_print(f"[info] Successfully downloaded audio-only: {filepath}", flush=True)
                                
                                # Validate and potentially fix the audio file
                                validated_filepath = self._validate_audio_file(filepath)
                                
                                return {
                                    'success': True,
                                    'error': None,
                                    'file_path': validated_filepath,
                                    'video_path': None,
                                    'audio_path': None,
                                    'separate_av': False,
                                    'needs_extraction': False,
                                    'file_size': os.path.getsize(validated_filepath)
                                }
                except Exception as e:
                    debug_print(f"[warning] Audio format {audio_fmt} failed: {e}", flush=True)
                    continue
            
            # Strategy 2: Try DASH/HLS formats which often separate audio/video
            # These formats will have audio without video
            debug_print(f"[info] Audio-only formats not available, trying DASH/HLS audio streams...", flush=True)
            dash_audio_formats = [
                'bestaudio',  # Most general selector first
                'bestaudio[protocol*=dash]/bestaudio',  # DASH with fallback
                'bestaudio[protocol*=m3u8]/bestaudio',  # HLS with fallback
                'bestaudio[protocol*=dash][ext=m4a]/bestaudio[protocol*=dash]/bestaudio',  # DASH M4A with fallbacks
                'bestaudio[protocol*=m3u8][ext=m4a]/bestaudio[protocol*=m3u8]/bestaudio',  # HLS M4A with fallbacks
                'bestaudio[ext=m4a]/bestaudio',  # Any M4A with fallback
                'bestaudio[ext=aac]/bestaudio',  # Any AAC with fallback
                'bestaudio[ext=mp3]/bestaudio',  # Any MP3 with fallback
            ]
            
            for dash_fmt in dash_audio_formats:
                if self.cancelled:
                    return self._cancelled_result()
                    
                try:
                    debug_print(f"[info] Trying DASH audio format: {dash_fmt}", flush=True)
                    ydl_opts = {
                        'format': dash_fmt,
                        'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
                        'progress_hooks': [progress_tracker],
                        'quiet': not DEBUG_MODE,  # Quiet in release builds
                        'no_warnings': not DEBUG_MODE,  # No warnings in release builds
                        'verbose': DEBUG_MODE,  # Verbose only in debug mode
                        'postprocessors': [],
                        'retries': 10,
                        'fragment_retries': 10,
                        'skip_unavailable_fragments': True,
                        'socket_timeout': 30,
                        'ignoreerrors': True,
                        'no-direct-merge': True,  # Don't merge video/audio - we handle that with MediaMuxer
                        # Audio-specific options for better compatibility
                        'prefer_insecure': False,
                        'http_chunk_size': 10485760,  # 10MB chunks for better streaming
                        'concurrent_fragment_downloads': 1,  # Single fragment to avoid corruption
                        'keep_fragments': False,
                        'writeinfojson': False,
                        'writethumbnail': False,
                        'writesubtitles': False,
                        'writeautomaticsub': False,
                    }
                    
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                        info = ydl.extract_info(url, download=True)
                        
                        if info and 'requested_downloads' in info and len(info['requested_downloads']) > 0:
                            filepath = info['requested_downloads'][0].get('filepath')
                            
                            if filepath and os.path.exists(filepath):
                                debug_print(f"[info] Successfully downloaded DASH audio: {filepath}", flush=True)
                                
                                # Validate and potentially fix the audio file
                                validated_filepath = self._validate_audio_file(filepath)
                                
                                return {
                                    'success': True,
                                    'error': None,
                                    'file_path': validated_filepath,
                                    'video_path': None,
                                    'audio_path': None,
                                    'separate_av': False,
                                    'needs_extraction': False,
                                    'file_size': os.path.getsize(validated_filepath)
                                }
                except Exception as e:
                    debug_print(f"[warning] DASH audio format {dash_fmt} failed: {e}", flush=True)
                    continue
            
            # Strategy 3: No audio-only formats available, download video+audio and signal extraction needed
            debug_print(f"[info] No audio-only formats available, downloading video+audio for extraction...", flush=True)
            
            try:
                ydl_opts = {
                    'format': 'best[height<=720]',  # Limit quality to save bandwidth for audio extraction
                    'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
                    'progress_hooks': [progress_tracker],
                    'quiet': False,
                    'no_warnings': False,
                    'verbose': True,  # Enable verbose logging for debugging
                    'postprocessors': [],
                    'retries': 10,
                    'fragment_retries': 10,
                    'skip_unavailable_fragments': True,
                    'socket_timeout': 30,
                    'ignoreerrors': True,
                    'no-direct-merge': True,  # Don't merge video/audio - we handle that with MediaMuxer
                }
                
                with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                    info = ydl.extract_info(url, download=True)
                    
                    if info and 'requested_downloads' in info and len(info['requested_downloads']) > 0:
                        filepath = info['requested_downloads'][0].get('filepath')
                        
                        if filepath and os.path.exists(filepath):
                            debug_print(f"[info] Downloaded video+audio for extraction: {filepath}", flush=True)
                            return {
                                'success': True,
                                'error': None,
                                'file_path': filepath,
                                'video_path': None,
                                'audio_path': None,
                                'separate_av': False,
                                'needs_extraction': True,  # Signal that audio extraction is needed
                                'file_size': os.path.getsize(filepath)
                            }
            except Exception as e:
                error_msg = f"Failed to download video+audio for extraction: {str(e)}"
                debug_print_stderr(f"[error] {error_msg}", flush=True)
                traceback.print_exc(file=sys.stderr)
            
            # All strategies failed
            return {
                'success': False,
                'error': 'No audio formats available and could not download video for extraction',
                'file_path': None,
                'video_path': None,
                'audio_path': None,
                'separate_av': False,
                'needs_extraction': False,
                'file_size': 0
            }
            
        except Exception as e:
            error_msg = str(e)
            debug_print_stderr(f"[error] Audio download failed: {error_msg}", flush=True)
            traceback.print_exc(file=sys.stderr)
            
            return {
                'success': False,
                'error': error_msg,
                'file_path': None,
                'video_path': None,
                'audio_path': None,
                'separate_av': False,
                'needs_extraction': False,
                'file_size': 0
            }
    
    def _validate_audio_file(self, filepath: str) -> str:
        """
        Validate and potentially fix audio file format issues
        Returns the path to a valid audio file (original or fixed)
        """
        try:
            debug_print(f"[info] Validating audio file: {filepath}", flush=True)
            
            # Check file size - if it's too small, it might be corrupted
            file_size = os.path.getsize(filepath)
            if file_size < 1024:  # Less than 1KB is suspicious
                debug_print(f"[warning] Audio file is very small ({file_size} bytes), might be corrupted", flush=True)
                return filepath  # Return original, let caller handle
            
            # Check file extension
            file_ext = os.path.splitext(filepath)[1].lower()
            
            # For m4a files, try to ensure proper container format
            if file_ext in ['.m4a', '.mp4']:
                # Try to read the file and check if it's properly formatted
                try:
                    with open(filepath, 'rb') as f:
                        # Read first few bytes to check for proper container headers
                        header = f.read(8)
                        
                        # Check for MP4/M4A container signatures
                        if header.startswith(b'\x00\x00\x00\x18ftyp') or \
                           header.startswith(b'\x00\x00\x00\x20ftyp') or \
                           header.startswith(b'\x00\x00\x00\x1cftyp'):
                            debug_print(f"[info] Audio file has proper MP4/M4A container format", flush=True)
                            return filepath
                        else:
                            debug_print(f"[warning] Audio file may not have proper container format, attempting repair", flush=True)
                            # Try to repair the file by adding proper container headers
                            repaired_filepath = self._repair_audio_container(filepath)
                            return repaired_filepath
                            
                except Exception as e:
                    debug_print(f"[warning] Could not validate container format: {e}", flush=True)
                    return filepath
            
            # For other audio formats, assume they're fine
            debug_print(f"[info] Audio file validation passed", flush=True)
            return filepath
            
        except Exception as e:
            debug_print_stderr(f"[error] Error validating audio file: {e}", flush=True)
            return filepath  # Return original file on error
    
    def _repair_audio_container(self, filepath: str) -> str:
        """
        Attempt to repair audio container format issues
        Returns the path to the repaired file (or original if repair fails)
        """
        try:
            debug_print(f"[info] Attempting to repair audio container: {filepath}", flush=True)
            
            # Read the original file
            with open(filepath, 'rb') as f:
                content = f.read()
            
            # Check if it's an AAC stream without proper container
            # AAC files often start with ADTS headers
            if content.startswith(b'\xff\xf1') or content.startswith(b'\xff\xf9'):
                debug_print(f"[info] Detected AAC stream, attempting to wrap in M4A container", flush=True)
                
                # Create a new filename with .m4a extension
                base_name = os.path.splitext(filepath)[0]
                repaired_filepath = base_name + '.m4a'
                
                # For now, just rename the file to .m4a and hope for the best
                # In a more sophisticated implementation, we would use a library
                # to properly wrap the AAC stream in an M4A container
                try:
                    os.rename(filepath, repaired_filepath)
                    debug_print(f"[info] Renamed audio file to .m4a: {repaired_filepath}", flush=True)
                    return repaired_filepath
                except Exception as e:
                    debug_print(f"[warning] Could not rename file: {e}", flush=True)
                    return filepath
            
            # If we can't repair it, return the original
            debug_print(f"[info] Could not repair audio container, returning original", flush=True)
            return filepath
            
        except Exception as e:
            debug_print_stderr(f"[error] Error repairing audio container: {e}", flush=True)
            return filepath
    
    def _cancelled_result(self) -> Dict[str, Any]:
        """Return a standardized cancelled result"""
        return {
            'success': False,
            'error': 'Download cancelled by user',
            'file_path': None,
            'video_path': None,
            'audio_path': None,
            'separate_av': False,
            'needs_extraction': False,
            'file_size': 0
        }
    
    def _debug_available_formats(self, url: str) -> None:
        """Debug helper to list all available formats for troubleshooting"""
        try:
            debug_print(f"[DEBUG] Listing all available formats for: {url}", flush=True)
            
            with yt_dlp.YoutubeDL({'quiet': True, 'no_warnings': True}) as ydl:
                info = ydl.extract_info(url, download=False)
                if info and 'formats' in info:
                    formats = info.get('formats', [])
                    debug_print(f"[DEBUG] Found {len(formats)} total formats:", flush=True)
                    
                    for i, fmt in enumerate(formats):
                        format_id = fmt.get('format_id', 'unknown')
                        ext = fmt.get('ext', 'unknown')
                        resolution = fmt.get('resolution', 'unknown')
                        vcodec = fmt.get('vcodec', 'none')
                        acodec = fmt.get('acodec', 'none')
                        protocol = fmt.get('protocol', 'unknown')
                        format_note = fmt.get('format_note', '')
                        
                        debug_print(f"[DEBUG] Format {i}: {format_id} - {resolution} - .{ext} - {vcodec}/{acodec} - {protocol} - {format_note}", flush=True)
                        
                        # Highlight audio-only formats
                        has_video = vcodec not in ('none', None, 'null') and vcodec != ''
                        has_audio = acodec not in ('none', None, 'null') and acodec != ''
                        
                        if has_audio and not has_video:
                            debug_print(f"[DEBUG]   ^^^ AUDIO-ONLY FORMAT ^^^", flush=True)
                        elif has_video and not has_audio:
                            debug_print(f"[DEBUG]   ^^^ VIDEO-ONLY FORMAT ^^^", flush=True)
                        elif has_video and has_audio:
                            debug_print(f"[DEBUG]   ^^^ COMPLETE FORMAT ^^^", flush=True)
                else:
                    debug_print(f"[DEBUG] No format information available", flush=True)
        except Exception as e:
            debug_print(f"[DEBUG] Error listing formats: {e}", flush=True)


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

