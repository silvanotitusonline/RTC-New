#!/usr/bin/env python3
"""Package the supplied full PNG without cropping, tracing, recoloring or redrawing."""
from pathlib import Path
import hashlib
import math
import shutil
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
SOURCE = RES / 'drawable-nodpi/rtc_community_logo_transparent.png'


def contained_logo(source: Image.Image, size: int, diameter: float) -> Image.Image:
    # Fit the WHOLE source rectangle inside the platform's safe circle. The
    # source's transparent margins remain present, and its aspect ratio is kept.
    scale = diameter / math.hypot(*source.size)
    width, height = (max(1, int(value * scale)) for value in source.size)
    resized = source.resize((width, height), Image.Resampling.LANCZOS)
    canvas = Image.new('RGBA', (size, size))
    canvas.alpha_composite(resized, ((size-width)//2, (size-height)//2))
    return canvas


def main():
    source = Image.open(SOURCE)
    if source.mode != 'RGBA' or source.getchannel('A').getextrema()[0] != 0:
        raise ValueError('The canonical logo must be a transparent RGBA PNG')
    shutil.copyfile(SOURCE, RES / 'drawable-nodpi/rtc_logo_mark_transparent.png')
    contained_logo(source, 1152, 752).save(RES / 'drawable-nodpi/rtc_splash_logo.png')
    for density, factor in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
        directory = RES / f'mipmap-{density}'
        contained_logo(source, int(108*factor), 64*factor).save(directory / 'ic_launcher_foreground.png')
        size = int(48*factor)
        logo = contained_logo(source, size, size*0.92)
        canvas = Image.new('RGBA', (size,size), '#0C1013')
        canvas.alpha_composite(logo)
        for name in ['ic_launcher.webp','ic_launcher_round.webp']:
            canvas.save(directory/name, format='WEBP', lossless=True)
    print('Canonical full PNG SHA256:', hashlib.sha256(SOURCE.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
