"""
Format extraction and processing for yt-dlp
Handles video format information parsing and quality selection
"""

from typing import List, Dict, Any
from ytdlp_utils import debug_print


class FormatProcessor:
    """Process and extract format information from video metadata"""
    
    def extract_format_info(self, formats: list) -> list:
        """Extract useful format information, intelligently pairing DASH video/audio streams"""
        format_list = []
        
        debug_print(f"[DEBUG] Processing {len(formats)} formats for quality selection", flush=True)
        
        for fmt in formats:
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
        has_video = vcodec not in ('none', None, 'null') and vcodec != ''
        has_audio = acodec not in ('none', None, 'null') and acodec != ''
        
        # Special case: yt-dlp generic extractor often reports null codecs for direct video links
        video_extensions = ['mp4', 'webm', 'mkv', 'avi', 'mov', 'flv', 'm4v', 'mpg', 'mpeg', 'wmv']
        audio_extensions = ['mp3', 'aac', 'm4a', 'wav', 'ogg', 'opus', 'flac']
        
        if ext in video_extensions and not has_video and not has_audio:
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
            resolution = fmt.get('resolution', 'unknown')
            if resolution in ('unknown', None, 'null'):
                if has_video:
                    quality_label = "Original Quality"
                    height = 9999  # Use high value for sorting
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
            'tbr': fmt.get('tbr', 0) or 0,
            'url': fmt.get('url', ''),
        }
        
        debug_print(f"[DEBUG] Added format {format_id}: {quality_label} ({resolution}) - has_video={has_video}, has_audio={has_audio}", flush=True)
        return format_info
    
    def get_quality_based_format(self, quality: str) -> str:
        """Get yt-dlp format string based on quality preference"""
        quality_map = {
            '2160p': 'best[height<=2160]/best',
            '1440p': 'best[height<=1440]/best', 
            '1080p': 'best[height<=1080]/best',
            '720p': 'best[height<=720]/best',
            '480p': 'best[height<=480]/best',
            '360p': 'best[height<=360]/best',
            'worst': 'worst',
            'best': 'best',
            'audio_mp3': 'bestaudio[ext=mp3]/bestaudio',
            'audio_aac': 'bestaudio[ext=m4a]/bestaudio[ext=aac]/bestaudio',
        }
        return quality_map.get(quality, 'best')
    
    def analyze_formats(self, formats: List[Dict[str, Any]]) -> Dict[str, bool]:
        """Analyze available formats to determine download strategy"""
        has_dash_streams = any('dash' in fmt.get('format_id', '').lower() or 
                             fmt.get('protocol') in ('http_dash_segments', 'm3u8_native', 'm3u8') 
                             for fmt in formats)
        has_audio_only = any(fmt.get('acodec', 'none') not in ('none', None, 'null') and 
                            fmt.get('vcodec', 'none') in ('none', None, 'null') 
                            for fmt in formats)
        has_video_only = any(fmt.get('vcodec', 'none') not in ('none', None, 'null') and 
                            fmt.get('acodec', 'none') in ('none', None, 'null') 
                            for fmt in formats)
        
        return {
            'has_dash_streams': has_dash_streams,
            'has_audio_only': has_audio_only,
            'has_video_only': has_video_only
        }

