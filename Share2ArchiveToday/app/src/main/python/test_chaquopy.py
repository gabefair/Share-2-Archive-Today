#!/usr/bin/env python3
"""
Simple test script to verify Chaquopy and yt-dlp functionality
"""

def test_imports():
    """Test that required modules can be imported"""
    try:
        import yt_dlp
        print("✓ yt-dlp imported successfully")
        return True
    except ImportError as e:
        print(f"✗ Failed to import yt-dlp: {e}")
        return False

def test_ytdlp_basic():
    """Test basic yt-dlp functionality"""
    try:
        import yt_dlp
        
        # Create a simple yt-dlp instance
        ydl_opts = {
            'quiet': True,
            'no_warnings': True
        }
        
        ydl = yt_dlp.YoutubeDL(ydl_opts)
        print("✓ yt-dlp instance created successfully")
        return True
    except Exception as e:
        print(f"✗ yt-dlp basic test failed: {e}")
        return False

def main():
    """Run all tests"""
    print("Testing Chaquopy and yt-dlp integration...")
    
    success = True
    success &= test_imports()
    success &= test_ytdlp_basic()
    
    if success:
        print("✓ All tests passed!")
    else:
        print("✗ Some tests failed!")
    
    return success

if __name__ == "__main__":
    main()
