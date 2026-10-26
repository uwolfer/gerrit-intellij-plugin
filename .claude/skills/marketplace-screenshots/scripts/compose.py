#!/usr/bin/env python3
#
# Copyright 2026 Urs Wolfer
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""compose.py SHOTS_DIR: turns the raw captures into Marketplace screenshots.

Expects settings-raw.png and push-raw.png (full 1600x1000 captures with the dialog open)
and final-01.png, final-02.png, final-03.png (full captures); writes final-04/05.png with
the dialogs cropped onto a gradient, then marketplace-NN.png at 1200x760.
Dialog crop boxes are for the 1600x1000 display; re-measure them when a dialog changes size.
"""
import sys
from PIL import Image, ImageDraw, ImageFilter
S=sys.argv[1].rstrip('/')+'/'
def canvas():
    c=Image.new('RGB',(1600,1000))
    d=ImageDraw.Draw(c)
    for y in range(1000):
        t=y/999; d.line([(0,y),(1600,y)],fill=(int(30+20*t),int(34+14*t),int(52+34*t)))
    return c
def place(src,box,scale,out):
    im=Image.open(S+src).convert('RGB').crop(box)
    im=im.resize((int(im.width*scale),int(im.height*scale)),Image.LANCZOS)
    c=canvas()
    x=(1600-im.width)//2; y=(1000-im.height)//2
    m=Image.new('L',im.size,0); ImageDraw.Draw(m).rounded_rectangle([0,0,im.width-1,im.height-1],12,fill=255)
    sh=Image.new('RGBA',(1600,1000),(0,0,0,0))
    ImageDraw.Draw(sh).rounded_rectangle([x,y+14,x+im.width,y+im.height+14],12,fill=(0,0,0,170))
    sh=sh.filter(ImageFilter.GaussianBlur(24))
    c=Image.alpha_composite(c.convert('RGBA'),sh).convert('RGB')
    c.paste(im,(x,y),m); c.save(S+out)
place('settings-raw.png',(350,150,1250,845),1.3,'final-05.png')
place('push-raw.png',(400,6,1482,994),0.97,'final-04.png')
for n in ['01','02','03','04','05']:
    im=Image.open(S+f'final-{n}.png').convert('RGB').resize((1200,760),Image.LANCZOS)
    im.save(S+f'marketplace-{n}.png',optimize=True)
