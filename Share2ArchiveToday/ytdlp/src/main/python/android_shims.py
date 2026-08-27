"""Install Android-safe subprocess/pty stubs before yt-dlp imports anything."""
from __future__ import annotations

import os
import sys
import types


def install() -> None:
    """Block real subprocess (Chaquopy SIGSEGV via _posixsubprocess on some devices)."""

    class _StubPopen:
        def __init__(self, *args, **kwargs):
            self.args = args
            self.returncode = 1

        def __enter__(self):
            return self

        def __exit__(self, *args, **kwargs):
            return False

        def communicate(self, *args, **kwargs):
            text = kwargs.get("text") or kwargs.get("encoding")
            empty = "" if text else b""
            return empty, empty

        def kill(self, *args, **kwargs):
            pass

        def wait(self, *args, **kwargs):
            return self.returncode

        @classmethod
        def run(cls, *args, timeout=None, **kwargs):
            text = kwargs.get("text") or kwargs.get("encoding")
            empty = "" if text else b""
            return empty, empty, 1

    sub = types.ModuleType("subprocess")
    sub.Popen = _StubPopen
    sub.run = _StubPopen.run
    sub.PIPE = -1
    sub.STDOUT = -2
    sub.DEVNULL = -3
    sub.CalledProcessError = OSError
    sub.TimeoutExpired = TimeoutError
    sub.STARTUPINFO = type("STARTUPINFO", (), {"dwFlags": 0})
    sub.STARTF_USESHOWWINDOW = 1
    sub._android_stub = True
    sys.modules["subprocess"] = sub

    posix = types.ModuleType("_posixsubprocess")
    posix._android_stub = True
    sys.modules["_posixsubprocess"] = posix

    pty = types.ModuleType("pty")

    def _no_pty(*args, **kwargs):
        raise OSError("pty unavailable on Android")

    pty.openpty = _no_pty
    pty._android_stub = True
    sys.modules["pty"] = pty


# Chaquopy loads sitecustomize.py at interpreter startup; also safe to import directly.
if os.name == "posix":
    install()
