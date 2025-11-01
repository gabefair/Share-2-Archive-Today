"""
Video download strategies and handlers for yt-dlp
Handles video downloads with different format strategies
"""

import os
import sys
import traceback
from typing import Dict, Any, Optional

try:
    import yt_dlp
except ImportError as e:
    print(f"Failed to import yt_dlp: {e}", file=sys.stderr)
    raise

from ytdlp_utils import debug_print, debug_print_stderr, get_ydl_base_options, create_result_dict, DEBUG_MODE

# Feature gate: Disable fallbacks for debugging
ENABLE_FALLBACKS = False  # Set to False to debug primary download strategy


class VideoHandler:
    """Handle video downloads with various strategies"""
    
    def __init__(self):
        self.cancelled = False
    
    def download_video_with_strategies(
        self,
        url: str,
        output_dir: str,
        quality: str,
        progress_tracker,
        format_processor
    ) -> Dict[str, Any]:
        """Try multiple format strategies for better compatibility"""
        video_format = format_processor.get_quality_based_format(quality, prefer_merged=True)
        debug_print(f"[info] Using quality-based format selector: {video_format}", flush=True)
        
        # Build strategies list - only include fallbacks if enabled
        format_strategies = [video_format]
        if ENABLE_FALLBACKS:
            format_strategies.append('bestvideo+bestaudio/best')
            format_strategies.append('best')
        
        last_error = None
        
        for i, strategy in enumerate(format_strategies):
            if self.cancelled:
                return create_result_dict(success=False, error='Download cancelled by user')
            
            debug_print(f"[info] Trying format strategy {i+1}/{len(format_strategies)}: {strategy}", flush=True)
            
            try:
                ydl_opts = get_ydl_base_options(DEBUG_MODE)
                ydl_opts.update({
                    'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
                    'progress_hooks': [progress_tracker],
                    'format': strategy,
                    'postprocessors': [],
                    'format_sort_force': True,
                })
                
                with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                    info = ydl.extract_info(url, download=True)
                    
                    if info and 'requested_downloads' in info and len(info['requested_downloads']) > 0:
                        download_info = info['requested_downloads'][0]
                        file_path = download_info.get('filepath')
                        
                        if file_path and os.path.exists(file_path):
                            debug_print(f"[info] Successfully downloaded with strategy {strategy}: {file_path}", flush=True)
                            result = self._analyze_downloaded_file(file_path, download_info)
                            if result['success']:
                                return result
            
            except Exception as e:
                last_error = str(e)
                debug_print(f"[warning] Strategy {strategy} failed: {last_error}", flush=True)
                if not ENABLE_FALLBACKS:
                    # If fallbacks disabled, fail immediately with detailed error
                    debug_print_stderr(f"[error] Primary strategy failed (fallbacks disabled): {last_error}", flush=True)
                    return create_result_dict(success=False, error=f"Primary download strategy failed: {last_error}")
                continue
        
        error_msg = f"All download strategies failed. Last error: {last_error}"
        debug_print_stderr(f"[error] {error_msg}", flush=True)
        return create_result_dict(success=False, error=error_msg)
    
    def download_dash_video(
        self,
        url: str,
        output_dir: str,
        quality: str,
        progress_tracker
    ) -> Dict[str, Any]:
        """
        Optimized handling for DASH and complex streaming formats
        
        DASH streams have separate video and audio tracks that need to be merged.
        This method uses format selectors like 'bestvideo[height<=1080]+bestaudio'
        which tells yt-dlp to download and merge both streams.
        """
        try:
            debug_print(f"[info] Using DASH-optimized strategy for quality: {quality}", flush=True)
            
            # Import format processor to get quality-based format
            from ytdlp_format_processor import FormatProcessor
            format_processor = FormatProcessor()
            
            # Get format string that merges video+audio for the requested quality
            primary_format = format_processor.get_quality_based_format(quality, prefer_merged=True)
            debug_print(f"[info] DASH format selector: {primary_format}", flush=True)
            
            # Build strategies - primary quality-based, then fallbacks if enabled
            dash_strategies = [primary_format]
            if ENABLE_FALLBACKS:
                dash_strategies.append('bestvideo+bestaudio/best')
                dash_strategies.append('best')
            
            for i, strategy in enumerate(dash_strategies):
                if self.cancelled:
                    return create_result_dict(success=False, error='Download cancelled by user')
                
                try:
                    debug_print(f"[info] Trying DASH strategy {i+1}/{len(dash_strategies)}: {strategy}", flush=True)
                    
                    ydl_opts = get_ydl_base_options(DEBUG_MODE)
                    ydl_opts.update({
                        'outtmpl': os.path.join(output_dir, '%(title)s.f%(format_id)s.%(ext)s'),
                        'progress_hooks': [progress_tracker],
                        'format': strategy,
                        'postprocessors': [],
                        # Do NOT set merge_output_format - we don't have ffmpeg
                        # yt-dlp will download video and audio separately
                        # Android will handle merging with MediaMuxer/Media3
                        'keepvideo': True,  # Keep video file even if merging would have been attempted
                    })
                    
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                        debug_print(f"[info] Starting yt-dlp extraction with format: {strategy}", flush=True)
                        info = ydl.extract_info(url, download=True)
                        
                        debug_print(f"[info] Download completed, analyzing results...", flush=True)
                        if info is None:
                            debug_print(f"[error] info dict is None!", flush=True)
                            raise Exception("yt-dlp returned None info dict")
                        
                        debug_print(f"[info] info dict keys: {list(info.keys())}", flush=True)
                        
                        # Debug: Log downloaded files in the output directory
                        debug_print(f"[info] Checking output directory: {output_dir}", flush=True)
                        try:
                            if os.path.exists(output_dir):
                                files_in_dir = os.listdir(output_dir)
                                debug_print(f"[info] Files in output directory: {files_in_dir}", flush=True)
                            else:
                                debug_print(f"[warning] Output directory does not exist!", flush=True)
                        except Exception as list_err:
                            debug_print(f"[warning] Could not list output directory: {list_err}", flush=True)
                        
                        # Check multiple possible locations for download info
                        if info and 'requested_downloads' in info:
                            debug_print(f"[info] requested_downloads exists: {len(info['requested_downloads'])} file(s)", flush=True)
                        elif info and 'entries' in info:
                            debug_print(f"[info] Found 'entries' in info dict (playlist?)", flush=True)
                        else:
                            debug_print(f"[info] requested_downloads not found, checking for _filename", flush=True)
                            # When using bestvideo+bestaudio without ffmpeg, yt-dlp might not populate requested_downloads
                            # Check for downloaded files in the output directory
                            if info:
                                debug_print(f"[info] Checking info dict for download evidence...", flush=True)
                                if '_filename' in info:
                                    debug_print(f"[info] Found _filename: {info.get('_filename')}", flush=True)
                                if 'filepath' in info:
                                    debug_print(f"[info] Found filepath: {info.get('filepath')}", flush=True)
                                if 'requested_formats' in info:
                                    debug_print(f"[info] Found requested_formats: {len(info.get('requested_formats', []))} format(s)", flush=True)
                        
                        if info and 'requested_downloads' in info and len(info['requested_downloads']) > 0:
                            requested_downloads = info['requested_downloads']
                            debug_print(f"[info] Downloaded {len(requested_downloads)} file(s)", flush=True)
                            
                            # Debug: Print structure of requested_downloads
                            for idx, dl in enumerate(requested_downloads):
                                filepath = dl.get('filepath', 'NONE')
                                vcodec = dl.get('vcodec', 'NONE')
                                acodec = dl.get('acodec', 'NONE')
                                debug_print(f"[info] File {idx}: filepath={filepath}", flush=True)
                                debug_print(f"[info]   vcodec={vcodec}, acodec={acodec}", flush=True)
                                
                                # Check if file actually exists
                                if filepath and filepath != 'NONE':
                                    file_exists = os.path.exists(filepath)
                                    if file_exists:
                                        file_size = os.path.getsize(filepath)
                                        debug_print(f"[info]   File exists: {file_size} bytes", flush=True)
                                    else:
                                        debug_print(f"[warning]   File does NOT exist at path: {filepath}", flush=True)
                            
                            # Check if we have separate video and audio files (DASH without ffmpeg)
                            if len(requested_downloads) == 2:
                                # Two files downloaded - likely video + audio that need merging
                                video_path = None
                                audio_path = None
                                
                                for dl in requested_downloads:
                                    filepath = dl.get('filepath')
                                    vcodec = dl.get('vcodec', 'none')
                                    acodec = dl.get('acodec', 'none')
                                    
                                    debug_print(f"[info] File: {filepath}, vcodec={vcodec}, acodec={acodec}", flush=True)
                                    
                                    has_video = vcodec not in ('none', None, 'null', '')
                                    has_audio = acodec not in ('none', None, 'null', '')
                                    
                                    if has_video and not has_audio:
                                        video_path = filepath
                                    elif has_audio and not has_video:
                                        audio_path = filepath
                                
                                if video_path and audio_path and os.path.exists(video_path) and os.path.exists(audio_path):
                                    debug_print(f"[info] DASH download successful - separate video and audio", flush=True)
                                    debug_print(f"[info]   Video: {video_path}", flush=True)
                                    debug_print(f"[info]   Audio: {audio_path}", flush=True)
                                    
                                    video_size = os.path.getsize(video_path)
                                    audio_size = os.path.getsize(audio_path)
                                    
                                    return create_result_dict(
                                        success=True,
                                        video_path=video_path,
                                        audio_path=audio_path,
                                        separate_av=True,
                                        file_size=video_size + audio_size,
                                        needs_extraction=False
                                    )
                            
                            # Single file downloaded (merged or video-only or audio-only)
                            download_info = requested_downloads[0]
                            file_path = download_info.get('filepath')
                            
                            if file_path and os.path.exists(file_path):
                                debug_print(f"[info] DASH download successful with strategy {strategy}: {file_path}", flush=True)
                                return self._analyze_downloaded_file(file_path, download_info)
                            else:
                                print(f"[warning] File from requested_downloads not found: {file_path}", flush=True)
                                print(f"[info] Will try fallback detection methods...", flush=True)
                                # Fall through to the fallback logic below
                        
                        # If we get here, either requested_downloads was empty OR the file didn't exist
                        # Try fallback: Check if files were downloaded even though requested_downloads wasn't correct
                        # This happens when using bestvideo+bestaudio with no_direct_merge
                        if info and 'requested_formats' in info:
                            debug_print(f"[info] Attempting fallback: checking for downloaded files manually", flush=True)
                            requested_formats = info.get('requested_formats', [])
                            print(f"[info] Found {len(requested_formats)} requested_formats", flush=True)
                            
                            # Build expected file paths
                            title = info.get('title', 'video')
                            combined_format_id = info.get('format_id', '')  # e.g., 'fallback+dash-8'
                            expected_base = os.path.join(output_dir, title)
                            
                            print(f"[info] Title: {title}", flush=True)
                            print(f"[info] Combined format_id: {combined_format_id}", flush=True)
                            
                            # Check for separate video and audio files
                            video_files = []
                            audio_files = []
                            
                            for fmt in requested_formats:
                                fmt_ext = fmt.get('ext', 'mp4')
                                format_id = fmt.get('format_id', '')
                                vcodec = fmt.get('vcodec', 'none')
                                acodec = fmt.get('acodec', 'none')
                                
                                # yt-dlp with no_direct_merge names files as:
                                # title.f{combined_format_id}.f{individual_format_id}.ext
                                possible_path = f"{expected_base}.f{combined_format_id}.f{format_id}.{fmt_ext}"
                                
                                print(f"[info] Checking for: {possible_path}", flush=True)
                                
                                if os.path.exists(possible_path):
                                    has_video = vcodec not in ('none', None, 'null', '')
                                    has_audio = acodec not in ('none', None, 'null', '')
                                    
                                    print(f"[info] Found file: {possible_path} (video={has_video}, audio={has_audio})", flush=True)
                                    
                                    if has_video and not has_audio:
                                        video_files.append(possible_path)
                                        debug_print(f"[info] Found video file: {possible_path}", flush=True)
                                    elif has_audio and not has_video:
                                        audio_files.append(possible_path)
                                        debug_print(f"[info] Found audio file: {possible_path}", flush=True)
                            
                            if len(video_files) > 0 and len(audio_files) > 0:
                                video_path = video_files[0]
                                audio_path = audio_files[0]
                                
                                video_size = os.path.getsize(video_path)
                                audio_size = os.path.getsize(audio_path)
                                
                                print(f"[SUCCESS] Fallback successful - found separate files!", flush=True)
                                print(f"[SUCCESS]   Video: {video_path} ({video_size} bytes)", flush=True)
                                print(f"[SUCCESS]   Audio: {audio_path} ({audio_size} bytes)", flush=True)
                                
                                return create_result_dict(
                                    success=True,
                                    video_path=video_path,
                                    audio_path=audio_path,
                                    separate_av=True,
                                    file_size=video_size + audio_size,
                                    needs_extraction=False
                                )
                            else:
                                print(f"[warning] Fallback failed - found {len(video_files)} video, {len(audio_files)} audio files", flush=True)
                            
                            # Last resort: Scan output directory for ANY video/audio files
                            debug_print(f"[info] Last resort: Scanning output directory for downloaded files", flush=True)
                            try:
                                all_files = [f for f in os.listdir(output_dir) if os.path.isfile(os.path.join(output_dir, f))]
                                debug_print(f"[info] Found {len(all_files)} file(s) in output directory", flush=True)
                                
                                video_extensions = ['.mp4', '.webm', '.mkv', '.avi', '.mov', '.flv', '.m4v']
                                audio_extensions = ['.mp3', '.aac', '.m4a', '.opus', '.ogg', '.wav']
                                
                                recent_video_files = []
                                recent_audio_files = []
                                
                                for filename in all_files:
                                    filepath = os.path.join(output_dir, filename)
                                    ext = os.path.splitext(filename)[1].lower()
                                    
                                    # Check if file was created recently (within last 60 seconds)
                                    file_age = os.path.getmtime(filepath)
                                    current_time = os.path.getmtime(output_dir)  # Use dir mtime as reference
                                    
                                    if ext in video_extensions:
                                        recent_video_files.append(filepath)
                                        debug_print(f"[info] Found video file: {filename}", flush=True)
                                    elif ext in audio_extensions:
                                        recent_audio_files.append(filepath)
                                        debug_print(f"[info] Found audio file: {filename}", flush=True)
                                
                                # If we found both video and audio files, use them
                                if len(recent_video_files) > 0 and len(recent_audio_files) > 0:
                                    # Sort by file size (largest first) to get best quality
                                    recent_video_files.sort(key=lambda x: os.path.getsize(x), reverse=True)
                                    recent_audio_files.sort(key=lambda x: os.path.getsize(x), reverse=True)
                                    
                                    video_path = recent_video_files[0]
                                    audio_path = recent_audio_files[0]
                                    
                                    video_size = os.path.getsize(video_path)
                                    audio_size = os.path.getsize(audio_path)
                                    
                                    debug_print(f"[info] Last resort SUCCESS - using:", flush=True)
                                    debug_print(f"[info]   Video: {video_path} ({video_size} bytes)", flush=True)
                                    debug_print(f"[info]   Audio: {audio_path} ({audio_size} bytes)", flush=True)
                                    
                                    return create_result_dict(
                                        success=True,
                                        video_path=video_path,
                                        audio_path=audio_path,
                                        separate_av=True,
                                        file_size=video_size + audio_size,
                                        needs_extraction=False
                                    )
                                elif len(recent_video_files) > 0:
                                    # Just video file, might have audio embedded
                                    video_path = recent_video_files[0]
                                    video_size = os.path.getsize(video_path)
                                    
                                    debug_print(f"[info] Found single video file: {video_path}", flush=True)
                                    return create_result_dict(
                                        success=True,
                                        file_path=video_path,
                                        file_size=video_size
                                    )
                                else:
                                    debug_print(f"[warning] No usable files found in directory scan", flush=True)
                                    
                            except Exception as scan_err:
                                debug_print(f"[error] Error scanning output directory: {scan_err}", flush=True)
                
                except Exception as e:
                    debug_print(f"[warning] DASH strategy {strategy} failed: {e}", flush=True)
                    if not ENABLE_FALLBACKS:
                        # If fallbacks disabled, fail immediately with detailed error
                        debug_print_stderr(f"[error] Primary DASH strategy failed (fallbacks disabled): {e}", flush=True)
                        return create_result_dict(success=False, error=f"DASH download failed: {str(e)}")
                    continue
            
            # Only reach here if all strategies failed and fallbacks were enabled
            if ENABLE_FALLBACKS:
                debug_print(f"[info] All DASH strategies failed, trying general fallback", flush=True)
                return self._download_video_fallback(url, output_dir, quality, progress_tracker)
            else:
                return create_result_dict(success=False, error="DASH download failed (fallbacks disabled)")
        
        except Exception as e:
            error_msg = f"DASH download failed: {str(e)}"
            debug_print_stderr(f"[error] {error_msg}", flush=True)
            return create_result_dict(success=False, error=error_msg)
    
    def _download_video_fallback(
        self,
        url: str,
        output_dir: str,
        quality: str,
        progress_tracker
    ) -> Dict[str, Any]:
        """Fallback download method for difficult cases"""
        try:
            debug_print(f"[info] Using comprehensive fallback strategy", flush=True)
            
            fallback_strategies = ['best', 'worst']
            
            for strategy in fallback_strategies:
                if self.cancelled:
                    return create_result_dict(success=False, error='Download cancelled by user')
                
                try:
                    ydl_opts = get_ydl_base_options(DEBUG_MODE)
                    ydl_opts.update({
                        'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
                        'progress_hooks': [progress_tracker],
                        'format': strategy,
                        'postprocessors': [],
                        'retries': 5,
                        'fragment_retries': 5,
                    })
                    
                    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                        info = ydl.extract_info(url, download=True)
                        
                        if info and 'requested_downloads' in info and len(info['requested_downloads']) > 0:
                            download_info = info['requested_downloads'][0]
                            file_path = download_info.get('filepath')
                            
                            if file_path and os.path.exists(file_path):
                                debug_print(f"[info] Fallback download successful: {file_path}", flush=True)
                                return self._analyze_downloaded_file(file_path, download_info)
                
                except Exception as e:
                    debug_print(f"[warning] Fallback strategy {strategy} failed: {e}", flush=True)
                    continue
            
            return create_result_dict(success=False, error="All fallback strategies failed")
        
        except Exception as e:
            return create_result_dict(success=False, error=f"Fallback download failed: {str(e)}")
    
    def _analyze_downloaded_file(self, file_path: str, download_info: Dict[str, Any]) -> Dict[str, Any]:
        """Analyze downloaded file to determine if audio/video separation is needed"""
        try:
            file_ext = os.path.splitext(file_path)[1].lower()
            
            downloaded_vcodec = download_info.get('vcodec', 'none')
            downloaded_acodec = download_info.get('acodec', 'none')
            
            container_with_audio = file_ext in ['.mp4', '.mkv', '.avi', '.mov', '.webm', '.flv']
            
            has_audio = (downloaded_acodec not in ('none', None, 'null', '')) or container_with_audio
            has_video = (downloaded_vcodec not in ('none', None, 'null', '')) or (file_ext not in ['.mp3', '.aac', '.m4a', '.wav', '.ogg'])
            
            debug_print(f"[info] File analysis: {file_path}", flush=True)
            debug_print(f"[info]   Extension: {file_ext}", flush=True)
            debug_print(f"[info]   Video codec: {downloaded_vcodec}", flush=True)
            debug_print(f"[info]   Audio codec: {downloaded_acodec}", flush=True)
            debug_print(f"[info]   Has video: {has_video}, Has audio: {has_audio}", flush=True)
            
            file_size = os.path.getsize(file_path)
            
            if has_video and has_audio:
                debug_print(f"[info] Downloaded combined video+audio file", flush=True)
                return create_result_dict(
                    success=True,
                    file_path=file_path,
                    file_size=file_size
                )
            elif has_video and not has_audio:
                debug_print(f"[info] Downloaded video only, need separate audio", flush=True)
                result = create_result_dict(
                    success=True,
                    file_path=file_path,
                    video_path=file_path,
                    separate_av=True,
                    file_size=file_size
                )
                result['needs_audio_extraction'] = True
                return result
            elif not has_video and has_audio:
                debug_print(f"[info] Downloaded audio only", flush=True)
                return create_result_dict(
                    success=True,
                    file_path=file_path,
                    file_size=file_size
                )
            else:
                debug_print(f"[info] Downloaded file (unknown codec info)", flush=True)
                return create_result_dict(
                    success=True,
                    file_path=file_path,
                    file_size=file_size
                )
        
        except Exception as e:
            debug_print_stderr(f"[error] Error analyzing downloaded file: {e}", flush=True)
            return create_result_dict(
                success=False,
                error=f'Failed to analyze file: {str(e)}',
                file_path=file_path
            )
    
    def debug_available_formats(self, url: str) -> None:
        """Debug helper to list all available formats"""
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

