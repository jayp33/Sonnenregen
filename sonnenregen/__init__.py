"""Extract sun and rain timings from WetterOnline forecasts."""

from .core import analyze, ParseError, SourceError

__all__ = ["analyze", "ParseError", "SourceError"]
__version__ = "0.1.0"
