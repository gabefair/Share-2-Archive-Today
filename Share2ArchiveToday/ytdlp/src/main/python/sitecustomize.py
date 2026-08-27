"""Run before any app Python code so subprocess never loads _posixsubprocess."""
import android_shims

android_shims.install()
