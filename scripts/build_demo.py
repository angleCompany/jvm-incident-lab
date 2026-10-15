#!/usr/bin/env python3
"""Build a 30-second edited walkthrough from actual JVM experiment results.
Optional media tooling only: Python 3.10+, Pillow, ffmpeg. Not needed to run the lab.
"""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
WIDTH, HEIGHT, FPS, SECONDS = 1280, 720, 24, 30
BG, PANEL, LINE = '#10171d', '#19232c', '#30404d'
INK, MUTED, GREEN, RED = '#edf3f6', '#a3b5c3', '#8ce4c5', '#ffac91'


def font(size, bold=False):
    names = (["/System/Library/Fonts/Supplemental/Arial Bold.ttf",
              "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"] if bold else
             ["/System/Library/Fonts/Supplemental/Arial.ttf",
              "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"])
    for name in names:
        if Path(name).is_file():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default(size=size)


def text(draw, xy, value, size=24, color=INK, bold=False):
    draw.text(xy, value, fill=color, font=font(size, bold), spacing=12)


def panel(draw, bounds, title, color=GREEN):
    draw.rounded_rectangle(bounds, radius=16, fill=PANEL, outline=LINE, width=2)
    text(draw, (bounds[0]+24, bounds[1]+20), title, 20, color, True)


def metrics(draw, x, y, r, width=520):
    for i, (key, label) in enumerate([('completed', 'parents completed / 4'),
                                    ('childrenStarted', 'children started'),
                                    ('queued', 'children queued')]):
        xx = x+i*(width//3)
        text(draw, (xx, y), str(r[key]), 60, GREEN if r['mode']=='fixed' else RED, True)
        text(draw, (xx, y+78), label, 15, MUTED)


def frame(t, failure, fixed):
    im = Image.new('RGB', (WIDTH, HEIGHT), BG)
    d = ImageDraw.Draw(im)
    text(d, (48, 30), 'JVM INCIDENT LAB', 21, INK, True)
    text(d, (940, 33), 'INCIDENT 001 / JAVA 21+', 16, GREEN)
    d.line((48, 73, 1232, 73), fill=LINE, width=2)
    if t < 4:
        text(d, (70, 130), 'Four workers.', 76, INK, True)
        text(d, (70, 220), 'Zero progress.', 76, GREEN, True)
        text(d, (74, 350), 'A runnable lab for thread pool starvation.', 32, MUTED)
        panel(d, (70, 435, 1210, 565), 'REPRODUCE  ->  READ THE EVIDENCE  ->  VERIFY THE FIX')
        text(d, (96, 494), './jvmlab run compare', 29)
    elif t < 11:
        text(d, (50, 104), '01 / Reproduce the failure', 39, INK, True)
        text(d, (50, 166), 'Parents submit child work to the same four-worker pool.', 25, MUTED)
        for i in range(4):
            x = 50+i*300
            panel(d, (x, 238, x+280, 330), 'PARENT '+str(i+1), RED)
            text(d, (x+24, 278), 'Future.get() -> WAIT', 19)
            d.line((x+140, 340, x+140, 373), fill=RED, width=3)
            panel(d, (x, 382, x+280, 470), 'CHILD '+str(i+1), MUTED)
            text(d, (x+24, 424), 'QUEUED / no worker', 18, MUTED)
        metrics(d, 80, 505, failure, 1100)
    elif t < 18:
        text(d, (50, 104), '02 / Read the evidence', 39, INK, True)
        text(d, (50, 166), 'Actual worker stack, correlated with executor counters.', 25, MUTED)
        panel(d, (50, 225, 1230, 500), 'ACTUAL THREAD SNAPSHOT', RED)
        lines = failure['dump'].splitlines()
        selected = [next((line for line in lines if 'WAITING' in line), 'WAITING')]
        selected += [line.strip() for line in lines if 'FutureTask.get' in line][:1]
        selected += [line.strip() for line in lines if 'Experiment.lambda' in line][:1]
        selected += ['', f"active parents: {failure['active']}    queued children: {failure['queued']}    child starts: {failure['childrenStarted']}"]
        # Show source locations and actual thread state, without local machine paths or run identifiers.
        selected[0] = 'parent worker  /  '+selected[0].rsplit(' ', 1)[-1]
        for i,line in enumerate(selected):
            text(d, (80, 286+i*35), line, 22, GREEN if i==1 else INK)
        text(d, (60, 538), 'All workers are waiting. The queued children cannot start.', 28, RED, True)
        text(d, (60, 589), 'Evidence saved: threads.txt + recording.jfr + report.html', 23, MUTED)
    elif t < 25:
        text(d, (50, 104), '03 / Verify the fix', 39, INK, True)
        panel(d, (50, 185, 625, 350), 'BEFORE / SHARED EXECUTOR', RED)
        text(d, (76, 250), 'parentPool.submit(work).get()', 25)
        panel(d, (655, 185, 1230, 350), 'AFTER / SEPARATE CHILD EXECUTOR')
        text(d, (681, 250), 'childPool.submit(work).get()', 25)
        panel(d, (50, 380, 625, 606), 'FAILURE / observed')
        metrics(d, 78, 441, failure)
        panel(d, (655, 380, 1230, 606), 'FIX / observed')
        metrics(d, 681, 441, fixed)
    else:
        text(d, (60, 112), 'Try the incident yourself.', 56, INK, True)
        text(d, (64, 197), 'JDK 21+  /  No Maven, database, or API key', 29, GREEN)
        panel(d, (60, 277, 1220, 495), 'QUICK START')
        text(d, (87, 338), 'git clone https://github.com/angleCompany/jvm-incident-lab.git', 24)
        text(d, (87, 385), 'cd jvm-incident-lab', 26)
        text(d, (87, 432), './jvmlab', 29, GREEN, True)
        text(d, (64, 537), 'Run failure -> fix. Then tell us where you got stuck.', 28, MUTED)
    # Every scene is labelled so edited timing cannot be mistaken for runtime evidence.
    text(d, (48, 666), 'ACTUAL JVM RESULTS / EDITED WALKTHROUGH / NOT A BENCHMARK', 15, MUTED)
    d.rectangle((0, 710, int(WIDTH*min(t/SECONDS, 1)), 719), fill=GREEN)
    return im


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT/'docs/media/demo.mp4')
    args = parser.parse_args()
    if shutil.which('ffmpeg') is None:
        raise SystemExit('ffmpeg is required to build the optional demo video')
    run = subprocess.run([str(ROOT/'jvmlab'), 'run', 'compare'], cwd=ROOT,
                         capture_output=True, text=True, check=True, timeout=45)
    results = [json.loads(line) for line in run.stdout.splitlines()]
    assert [r['mode'] for r in results] == ['starved', 'fixed']
    failure, fixed = results
    assert failure['expected'] and fixed['expected'], 'Do not render an unsuccessful experiment as a successful demo'
    assert (failure['completed'],failure['childrenStarted'],failure['queued']) == (0,0,4)
    assert (fixed['completed'],fixed['childrenStarted'],fixed['queued']) == (4,4,0)
    assert 'FutureTask.get' in failure['dump'], 'Actual wait evidence missing'
    args.output.parent.mkdir(parents=True, exist_ok=True)
    command = ['ffmpeg','-y','-hide_banner','-loglevel','error','-f','rawvideo','-pixel_format','rgb24',
               '-video_size',f'{WIDTH}x{HEIGHT}','-framerate',str(FPS),'-i','-',
               '-an','-c:v','libx264','-preset','fast','-crf','22','-pix_fmt','yuv420p',
               '-movflags','+faststart',str(args.output)]
    encoder = subprocess.Popen(command, stdin=subprocess.PIPE)
    try:
        for index in range(FPS*SECONDS):
            encoder.stdin.write(frame(index/FPS, failure, fixed).tobytes())
        encoder.stdin.close()
        if encoder.wait(timeout=60) != 0:
            raise RuntimeError('ffmpeg failed')
    except BaseException:
        encoder.kill()
        encoder.wait()
        raise
    frame(22, failure, fixed).save(args.output.with_name('demo-poster.png'))
    print(f'Created {args.output} ({SECONDS}s, {WIDTH}x{HEIGHT}, {FPS}fps, silent)')
    print('Underlying real evidence: '+failure['report']+' and '+fixed['report'])

if __name__ == '__main__':
    main()
