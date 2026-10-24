#!/usr/bin/env python3
#    Copyright 2013-2026, Sumeet Chhetri
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
# Generates the GATF brand assets. Usage: gen.py <space-grotesk-latin-700-normal.woff> <outdir>
import os, sys, io
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.transformPen import TransformPen
import cairosvg
from PIL import Image

FONT, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
INK, PAPER, ACC, ACC_D = "#0E1726", "#F3F5F9", "#0E9670", "#2BD39E"

def mark(ink, acc, sw=14, tx=0, ty=0, s=1.0):
    return (f'<g transform="translate({tx} {ty}) scale({s})">'
            f'<path d="M91.1 28.9 A44 44 0 1 0 91.1 91.1" fill="none" stroke="{ink}" stroke-width="{sw}" stroke-linecap="round"/>'
            f'<path d="M50 60 L66 76 L106 36" fill="none" stroke="{acc}" stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round"/></g>')

f = TTFont(FONT); gs = f.getGlyphSet(); cmap = f.getBestCmap(); upm = f['head'].unitsPerEm
def word(text, size, track=-0.04):
    sc = size / upm; x = 0; parts = []; xmin=ymin=1e9; xmax=ymax=-1e9
    for ch in text:
        g = cmap[ord(ch)]
        pen = SVGPathPen(gs)
        tp = TransformPen(pen, (sc, 0, 0, -sc, x, 0)); gs[g].draw(tp)
        bp = BoundsPen(gs); gs[g].draw(TransformPen(bp, (sc, 0, 0, -sc, x, 0)))
        if bp.bounds:
            a,b,c,d = bp.bounds; xmin=min(xmin,a); ymin=min(ymin,b); xmax=max(xmax,c); ymax=max(ymax,d)
        parts.append(pen.getCommands())
        x += gs[g].width * sc + track * size
    return " ".join(parts), (xmin, ymin, xmax, ymax)

def svg(w, h, body, title, bg=None, extra=""):
    b = f'<rect width="{w}" height="{h}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}" role="img" aria-label="{title}">'
            f'<title>{title}</title>{extra}{b}{body}</svg>\n')

def put(name, data):
    open(os.path.join(OUT, name), "w").write(data)

# marks
for n, ink, acc in [("gatf-mark", INK, ACC), ("gatf-mark-dark", PAPER, ACC_D), ("gatf-mark-mono-black", "#000", "#000"), ("gatf-mark-mono-white", "#FFF", "#FFF")]:
    put(n + ".svg", svg(120, 120, mark(ink, acc), "GATF"))
put("gatf-mark-16.svg", svg(120, 120, mark(INK, ACC, 16), "GATF"))

# horizontal lockup: mark 120, gap 20, wordmark size 86, glyph bbox centred on the mark
d, (x0, y0, x1, y1) = word("gatf", 86)
gap = 22; pad = 8
wx = 120 + gap - x0; wy = 60 - (y0 + y1) / 2
W = 120 + gap + (x1 - x0) + pad; H = 120
def lockup(ink, acc, wink):
    return mark(ink, acc) + f'<path transform="translate({wx:.2f} {wy:.2f})" d="{d}" fill="{wink}"/>'
for n, ink, acc in [("gatf-logo", INK, ACC), ("gatf-logo-dark", PAPER, ACC_D), ("gatf-logo-mono-black", "#000", "#000"), ("gatf-logo-mono-white", "#FFF", "#FFF")]:
    put(n + ".svg", svg(round(W), H, lockup(ink, acc, ink), "GATF"))

# stacked: mark centred above wordmark
ds, (sx0, sy0, sx1, sy1) = word("gatf", 72)
ww = sx1 - sx0; SW = max(120, ww) + 16; mx = (SW - 120) / 2
top = 120 + 18 - sy0
def stacked(ink, acc):
    return mark(ink, acc, tx=mx) + f'<path transform="translate({(SW-ww)/2 - sx0:.2f} {top:.2f})" d="{ds}" fill="{ink}"/>'
SH = top + sy1 + 8
for n, ink, acc in [("gatf-logo-stacked", INK, ACC), ("gatf-logo-stacked-dark", PAPER, ACC_D)]:
    put(n + ".svg", svg(round(SW), round(SH), stacked(ink, acc), "GATF"))

# app icon (rounded square, mark at 70 %)
icon = svg(512, 512, f'<rect width="512" height="512" rx="120" fill="{INK}"/>' + mark(PAPER, ACC_D, tx=76, ty=76, s=3.0), "GATF")
put("gatf-app-icon.svg", icon)

# favicon.svg that follows the browser theme
fav = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120"><style>.i{stroke:%s}.a{stroke:%s}'
       '@media (prefers-color-scheme:dark){.i{stroke:%s}.a{stroke:%s}}</style>'
       '<path class="i" d="M91.1 28.9 A44 44 0 1 0 91.1 91.1" fill="none" stroke-width="16" stroke-linecap="round"/>'
       '<path class="a" d="M50 60 L66 76 L106 36" fill="none" stroke-width="16" stroke-linecap="round" stroke-linejoin="round"/></svg>\n') % (INK, ACC, PAPER, ACC_D)
put("favicon.svg", fav)

# open-graph card 1200x630
og_d, (ox0, oy0, ox1, oy1) = word("gatf", 200)
ms = 2.4; mw = 120 * ms; gw = 50; tw = (ox1 - ox0) + mw + gw
sx = (1200 - tw) / 2
og = svg(1200, 630, mark(PAPER, ACC_D, tx=sx, ty=(630 - mw) / 2, s=ms) +
         f'<path transform="translate({sx + mw + gw - ox0:.2f} {315 - (oy0 + oy1) / 2:.2f})" d="{og_d}" fill="{PAPER}"/>', "GATF", bg=INK)
put("gatf-og.svg", og)

def png(src, name, size, w=None, h=None):
    cairosvg.svg2png(bytestring=open(os.path.join(OUT, src), "rb").read(), write_to=os.path.join(OUT, name),
                     output_width=w or size, output_height=h or size)
png("gatf-mark-16.svg", "favicon-16.png", 16)
png("gatf-mark.svg", "favicon-32.png", 32)
png("gatf-mark.svg", "favicon-48.png", 48)
png("gatf-app-icon.svg", "apple-touch-icon.png", 180)
png("gatf-app-icon.svg", "icon-192.png", 192)
png("gatf-app-icon.svg", "icon-512.png", 512)
png("gatf-og.svg", "gatf-og.png", 0, 1200, 630)
png("gatf-logo.svg", "gatf-logo@2x.png", 0, round(W) * 4, H * 4)
imgs = [Image.open(os.path.join(OUT, f"favicon-{s}.png")).convert("RGBA") for s in (16, 32, 48)]
imgs[2].save(os.path.join(OUT, "favicon.ico"), sizes=[(16, 16), (32, 32), (48, 48)], append_images=imgs[:2])
print("lockup", round(W), H, "stacked", round(SW), round(SH))
