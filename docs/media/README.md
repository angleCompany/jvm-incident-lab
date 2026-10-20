# 30-second demo

[Watch the MP4](demo.mp4)

This is a silent, **edited walkthrough of actual JVM experiment results**, not a real-time screen recording or performance comparison. Captions and diagrams illustrate the mechanism. The counters and displayed stack frames come from a fresh successful `./jvmlab run compare`; the media builder refuses unexpected results.

| Time | Scene |
|---|---|
| 0–4s | Four workers, zero progress |
| 4–11s | Shared executor: waiting parents and queued children |
| 11–18s | Actual worker stack and executor evidence |
| 18–25s | Separate child executor and measured outcomes |
| 25–30s | Clone and run |

## Rebuild

Optional media tooling: Python 3.10+, Pillow, ffmpeg with libx264. It is not required to run JVM Incident Lab.

```sh
python3 -m venv .media-venv
.media-venv/bin/pip install Pillow==12.2.0
.media-venv/bin/python scripts/build_demo.py
```

Install ffmpeg through your platform's package manager. The script uses Arial on macOS or DejaVu Sans on Linux, with a Pillow default-font fallback. Underlying thread/JFR/report evidence remains in ignored `artifacts/` directories.

## Repository social preview

`social-preview.png` is a 1280×640 PNG for GitHub link sharing. It illustrates the same controlled failure/fix counters. Rebuild with the optional Pillow environment above:

```sh
.media-venv/bin/python scripts/build_social_preview.py
```

Set it separately in repository Settings → Social preview; committing this asset alone does not change GitHub's preview.
