import tempfile
import sys
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from ui import MainWindow


class CapturePlayer:
    def __init__(self):
        self.url = None
        self.played = False

    def setSource(self, url):
        self.url = url

    def play(self):
        self.played = True


def open_video(source, audio=''):
    video = CapturePlayer()
    sound = CapturePlayer()
    window = SimpleNamespace(
        _video_player=video,
        _video_sound=sound,
        _cam_stop=SimpleNamespace(set=lambda: None),
        _video_title=SimpleNamespace(setText=lambda text: None),
        _hud_cam_stack=SimpleNamespace(setCurrentIndex=lambda index: None),
        _video_sync=SimpleNamespace(start=lambda: None),
        _set_video_muted=lambda muted: None,
        _sync_mic_for_video=lambda: None,
    )
    MainWindow._on_video_open(window, source, 'Test', True, audio)
    assert video.played
    return video.url, sound.url


stream = 'https://example.com/videoplayback?' + 'token=' + 'x' * 2000
video_url, _ = open_video(stream, 'https://example.com/audio')
assert video_url.toString() == stream
assert not video_url.isLocalFile()

with tempfile.NamedTemporaryFile(suffix='.mp4') as item:
    local_url, _ = open_video(item.name)
    assert local_url.isLocalFile()
    assert Path(local_url.toLocalFile()) == Path(item.name)

print('Long remote stream and local video path both open without an exception')
