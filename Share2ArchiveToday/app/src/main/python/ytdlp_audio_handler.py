"""
Audio download and validation logic for yt-dlp
Main coordinator for audio downloads - refactored for modularity
"""

import sys
import traceback
from typing import Dict, Any, Optional, Callable

from ytdlp_utils import debug_print, debug_print_stderr, create_result_dict, DEBUG_MODE
from ytdlp_audio_strategies import AudioDownloadStrategies


class AudioHandler:
    """Handle audio downloads - coordinates download strategies"""
    
    def __init__(self):
        self.cancelled = False
        self.strategies = AudioDownloadStrategies()
    
    def download_audio(
        self,
        url: str,
        output_dir: str,
        audio_format: str,
        progress_tracker,
        debug_available_formats_func: Optional[Callable] = None
    ) -> Dict[str, Any]:
        """
        Download audio only with fallback strategies
        Coordinates different download strategies for maximum compatibility
        """
        try:
            self.cancelled = False
            self.strategies.cancelled = False
            
            debug_print(f"[info] Starting audio download: {url}", flush=True)
            debug_print(f"[info] Output directory: {output_dir}", flush=True)
            debug_print(f"[info] Format: {audio_format}", flush=True)
            
            # Debug: List all available formats for troubleshooting
            if DEBUG_MODE and debug_available_formats_func:
                debug_available_formats_func(url)
            
            # Check available formats
            self.strategies.check_available_audio_formats(url)
            
            # Strategy 1: Try audio-only formats
            result = self.strategies.try_audio_only_formats(url, output_dir, progress_tracker)
            if result:
                return result
            
            # Strategy 2: Try DASH/HLS formats
            result = self.strategies.try_dash_audio_formats(url, output_dir, progress_tracker)
            if result:
                return result
            
            # Strategy 3: Download video+audio for extraction
            result = self.strategies.download_for_extraction(url, output_dir, progress_tracker)
            if result:
                return result
            
            # All strategies failed
            return create_result_dict(
                success=False,
                error='No audio formats available and could not download video for extraction'
            )
            
        except Exception as e:
            error_msg = str(e)
            debug_print_stderr(f"[error] Audio download failed: {error_msg}", flush=True)
            traceback.print_exc(file=sys.stderr)
            return create_result_dict(success=False, error=error_msg)
