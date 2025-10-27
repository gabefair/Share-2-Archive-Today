"""
Audio download strategies for yt-dlp
Contains different strategies for downloading audio from various sources
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
from ytdlp_audio_validator import AudioValidator


class AudioDownloadStrategies:
    """Collection of audio download strategies"""
    
    def __init__(self):
        self.cancelled = False
    
    def check_available_audio_formats(self, url: str):
        """Check what audio formats are available"""
        try:
            debug_print(f"[info] Checking available formats...", flush=True)
            with yt_dlp.YoutubeDL({'quiet': True}) as ydl:
                info = ydl.extract_info(url, download=False)
                formats = info.get('formats', [])
                
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
    
    def try_audio_only_formats(self, url: str, output_dir: str, progress_tracker) -> Optional[Dict[str, Any]]:
        """Try audio-only formats with better format selection"""
        audio_only_formats = [
            'bestaudio',
            'bestaudio[ext=m4a]/bestaudio[ext=aac]/bestaudio[ext=mp3]/bestaudio',
            'bestaudio[ext=m4a]/bestaudio',
            'bestaudio[ext=aac]/bestaudio',
            'bestaudio[ext=mp3]/bestaudio',
            'bestaudio[ext=opus]/bestaudio',
            'bestaudio[ext=ogg]/bestaudio',
            'bestaudio[protocol*=dash]/bestaudio',
            'bestaudio[protocol*=m3u8]/bestaudio',
        ]
        
        for audio_fmt in audio_only_formats:
            if self.cancelled:
                return create_result_dict(success=False, error='Download cancelled by user')
            
            try:
                debug_print(f"[info] Trying audio format: {audio_fmt}", flush=True)
                result = self._download_with_format(url, output_dir, audio_fmt, progress_tracker)
                if result:
                    return result
                    
            except Exception as e:
                debug_print(f"[warning] Audio format {audio_fmt} failed: {e}", flush=True)
                continue
        
        return None
    
    def try_dash_audio_formats(self, url: str, output_dir: str, progress_tracker) -> Optional[Dict[str, Any]]:
        """Try DASH/HLS formats which often separate audio/video"""
        debug_print(f"[info] Audio-only formats not available, trying DASH/HLS audio streams...", flush=True)
        
        dash_audio_formats = [
            'bestaudio',
            'bestaudio[protocol*=dash]/bestaudio',
            'bestaudio[protocol*=m3u8]/bestaudio',
            'bestaudio[protocol*=dash][ext=m4a]/bestaudio[protocol*=dash]/bestaudio',
            'bestaudio[protocol*=m3u8][ext=m4a]/bestaudio[protocol*=m3u8]/bestaudio',
            'bestaudio[ext=m4a]/bestaudio',
            'bestaudio[ext=aac]/bestaudio',
            'bestaudio[ext=mp3]/bestaudio',
        ]
        
        for dash_fmt in dash_audio_formats:
            if self.cancelled:
                return create_result_dict(success=False, error='Download cancelled by user')
                
            try:
                debug_print(f"[info] Trying DASH audio format: {dash_fmt}", flush=True)
                result = self._download_with_format(url, output_dir, dash_fmt, progress_tracker)
                if result:
                    return result
                    
            except Exception as e:
                debug_print(f"[warning] DASH audio format {dash_fmt} failed: {e}", flush=True)
                continue
        
        return None
    
    def download_for_extraction(self, url: str, output_dir: str, progress_tracker) -> Optional[Dict[str, Any]]:
        """Download video+audio for extraction when no audio-only formats available"""
        debug_print(f"[info] No audio-only formats available, downloading video+audio for extraction...", flush=True)
        
        try:
            ydl_opts = get_ydl_base_options(DEBUG_MODE)
            ydl_opts.update({
                'format': 'best[height<=720]',
                'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
                'progress_hooks': [progress_tracker],
                'postprocessors': [],
            })
            ydl_opts['quiet'] = False
            ydl_opts['no_warnings'] = False
            ydl_opts['verbose'] = True
            
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=True)
                
                if info and 'requested_downloads' in info and len(info['requested_downloads']) > 0:
                    filepath = info['requested_downloads'][0].get('filepath')
                    
                    if filepath and os.path.exists(filepath):
                        debug_print(f"[info] Downloaded video+audio for extraction: {filepath}", flush=True)
                        return create_result_dict(
                            success=True,
                            file_path=filepath,
                            needs_extraction=True,
                            file_size=os.path.getsize(filepath)
                        )
        except Exception as e:
            error_msg = f"Failed to download video+audio for extraction: {str(e)}"
            debug_print_stderr(f"[error] {error_msg}", flush=True)
            traceback.print_exc(file=sys.stderr)
        
        return None
    
    def _download_with_format(self, url: str, output_dir: str, audio_fmt: str, progress_tracker) -> Optional[Dict[str, Any]]:
        """Download audio with specific format string"""
        ydl_opts = get_ydl_base_options(DEBUG_MODE)
        ydl_opts.update({
            'format': audio_fmt,
            'outtmpl': os.path.join(output_dir, '%(title)s.%(ext)s'),
            'progress_hooks': [progress_tracker],
            'postprocessors': [],
            'format_sort_force': True,
            'http_chunk_size': 10485760,
            'concurrent_fragment_downloads': 1,
            'keep_fragments': False,
        })
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)
            
            if info and 'requested_downloads' in info and len(info['requested_downloads']) > 0:
                filepath = info['requested_downloads'][0].get('filepath')
                
                if filepath and os.path.exists(filepath):
                    debug_print(f"[info] Successfully downloaded audio: {filepath}", flush=True)
                    validated_filepath = AudioValidator.validate_audio_file(filepath)
                    return create_result_dict(
                        success=True,
                        file_path=validated_filepath,
                        file_size=os.path.getsize(validated_filepath)
                    )
        
        return None

