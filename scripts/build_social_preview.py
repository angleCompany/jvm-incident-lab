#!/usr/bin/env python3
"""Render the repository's 1280x640 social preview. Requires optional Pillow media tooling."""
from pathlib import Path
from PIL import Image, ImageDraw
from build_demo import font

ROOT = Path(__file__).resolve().parents[1]
image = Image.new('RGB', (1280, 640), '#10171d')
draw = ImageDraw.Draw(image)
def text(x, y, value, size, color='#edf3f6', bold=False):
    draw.text((x,y), value, font=font(size,bold), fill=color)
text(64, 40, 'JAVA 21+  /  LOCAL  /  OPEN SOURCE', 19, '#8ce4c5', True)
text(64, 92, 'JVM Incident Lab', 65, bold=True)
text(64, 181, 'Java thread pool starvation, reproduced.', 33, '#a3b5c3')
for x, title, number, label, color in [
    (64, 'FAILURE / SHARED POOL', '0 / 4', 'parent tasks complete', '#ffac91'),
    (666, 'FIX / SEPARATE CHILD POOL', '4 / 4', 'parent tasks complete', '#8ce4c5')]:
    draw.rounded_rectangle((x,266,x+550,480),radius=18,fill='#19232c',outline='#30404d',width=2)
    text(x+26,288,title,20,color,True)
    text(x+26,330,number,62,color,True)
    text(x+26,417,label,23,'#a3b5c3')
text(64,523,'Thread dumps + Java Flight Recorder (JFR) + verified fix',27)
text(64,577,'github.com/angleCompany/jvm-incident-lab',22,'#8ce4c5')
path=ROOT/'docs/media/social-preview.png'
image.save(path,optimize=True)
assert path.stat().st_size < 1_000_000
print(f'{path}: 1280x640, {path.stat().st_size} bytes')
