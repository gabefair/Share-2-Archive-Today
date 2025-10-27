"""
Audio file validation and repair utilities
Handles audio file format checking and container repair
"""

import os
from ytdlp_utils import debug_print, debug_print_stderr


class AudioValidator:
    """Validate and repair audio files"""
    
    @staticmethod
    def validate_audio_file(filepath: str) -> str:
        """
        Validate and potentially fix audio file format issues
        Returns the path to a valid audio file (original or fixed)
        """
        try:
            debug_print(f"[info] Validating audio file: {filepath}", flush=True)
            
            # Check file size
            file_size = os.path.getsize(filepath)
            if file_size < 1024:
                debug_print(f"[warning] Audio file is very small ({file_size} bytes), might be corrupted", flush=True)
                return filepath
            
            # Check file extension
            file_ext = os.path.splitext(filepath)[1].lower()
            
            # For m4a files, try to ensure proper container format
            if file_ext in ['.m4a', '.mp4']:
                return AudioValidator._validate_m4a_container(filepath)
            
            debug_print(f"[info] Audio file validation passed", flush=True)
            return filepath
            
        except Exception as e:
            debug_print_stderr(f"[error] Error validating audio file: {e}", flush=True)
            return filepath
    
    @staticmethod
    def _validate_m4a_container(filepath: str) -> str:
        """Validate M4A/MP4 container format"""
        try:
            with open(filepath, 'rb') as f:
                header = f.read(8)
                
                # Check for MP4/M4A container signatures
                if header.startswith(b'\x00\x00\x00\x18ftyp') or \
                   header.startswith(b'\x00\x00\x00\x20ftyp') or \
                   header.startswith(b'\x00\x00\x00\x1cftyp'):
                    debug_print(f"[info] Audio file has proper MP4/M4A container format", flush=True)
                    return filepath
                else:
                    debug_print(f"[warning] Audio file may not have proper container format, attempting repair", flush=True)
                    repaired_filepath = AudioValidator._repair_audio_container(filepath)
                    return repaired_filepath
                    
        except Exception as e:
            debug_print(f"[warning] Could not validate container format: {e}", flush=True)
            return filepath
    
    @staticmethod
    def _repair_audio_container(filepath: str) -> str:
        """
        Attempt to repair audio container format issues
        Returns the path to the repaired file (or original if repair fails)
        """
        try:
            debug_print(f"[info] Attempting to repair audio container: {filepath}", flush=True)
            
            with open(filepath, 'rb') as f:
                content = f.read()
            
            # Check if it's an AAC stream without proper container
            if content.startswith(b'\xff\xf1') or content.startswith(b'\xff\xf9'):
                return AudioValidator._wrap_aac_in_m4a(filepath)
            
            debug_print(f"[info] Could not repair audio container, returning original", flush=True)
            return filepath
            
        except Exception as e:
            debug_print_stderr(f"[error] Error repairing audio container: {e}", flush=True)
            return filepath
    
    @staticmethod
    def _wrap_aac_in_m4a(filepath: str) -> str:
        """Wrap AAC stream in M4A container (simple rename for now)"""
        debug_print(f"[info] Detected AAC stream, attempting to wrap in M4A container", flush=True)
        
        base_name = os.path.splitext(filepath)[0]
        repaired_filepath = base_name + '.m4a'
        
        try:
            os.rename(filepath, repaired_filepath)
            debug_print(f"[info] Renamed audio file to .m4a: {repaired_filepath}", flush=True)
            return repaired_filepath
        except Exception as e:
            debug_print(f"[warning] Could not rename file: {e}", flush=True)
            return filepath

