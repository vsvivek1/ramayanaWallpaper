from PIL import Image, ImageDraw, ImageFont, ImageFilter
R='/home/claude/renders/'; P='/home/claude/ramayanawallpaper/app/src/main/assets/paintings/'; O='/home/claude/store7/'
MAROON=(58,11,11); DEEP=(38,6,6); GOLD=(232,194,122); CREAM=(251,244,230); SAFF=(242,154,46); INK=(42,26,18)
SB='/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf'; SI='/usr/share/fonts/truetype/liberation/LiberationSerif-Italic.ttf'
SN='/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf'; SNB='/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf'
W,H=1080,1920
def font(p,s): return ImageFont.truetype(p,s)
def rounded(im,r):
    m=Image.new('L',im.size,0); ImageDraw.Draw(m).rounded_rectangle((0,0,im.width-1,im.height-1),r,fill=255); im=im.convert('RGBA'); im.putalpha(m); return im
def phone(src,h):
    im=Image.open(R+src).convert('RGB'); w=round(h*im.width/im.height); im=im.resize((w,h),Image.LANCZOS)
    pad=14; f=Image.new('RGBA',(w+2*pad,h+2*pad),(0,0,0,0)); d=ImageDraw.Draw(f)
    d.rounded_rectangle((0,0,w+2*pad-1,h+2*pad-1),56,fill=(22,14,12),outline=(90,70,50),width=2)
    f.alpha_composite(rounded(im,44),(pad,pad)); return f
def shadowed(base,obj,xy,blur=22,off=(12,22),alpha=0.6):
    s=Image.new('RGBA',base.size,(0,0,0,0)); a=obj.split()[-1]; sh=Image.new('RGBA',obj.size,(0,0,0,150)); sh.putalpha(a.point(lambda v:int(v*alpha)))
    s.alpha_composite(sh,(xy[0]+off[0],xy[1]+off[1])); s=s.filter(ImageFilter.GaussianBlur(blur)); base.alpha_composite(s); base.alpha_composite(obj,xy)
def cover(p,w,h):
    s=max(w/p.width,h/p.height); p=p.resize((round(p.width*s)+1,round(p.height*s)+1),Image.LANCZOS); l=(p.width-w)//2; t=(p.height-h)//2; return p.crop((l,t,l+w,t+h))
def bg_painting(pid,dark=0.72):
    p=Image.open(P+f'{pid}.jpg').convert('RGB'); p=cover(p,W,H).filter(ImageFilter.GaussianBlur(28))
    ov=Image.new('RGBA',(W,H),(0,0,0,0)); d=ImageDraw.Draw(ov)
    for y in range(H): a=int(255*(dark+0.22*y/H)); d.line((0,y,W,y),fill=(*DEEP,min(255,a)))
    im=p.convert('RGBA'); im.alpha_composite(ov); return im
def bg_cream():
    im=Image.new('RGBA',(W,H),CREAM); d=ImageDraw.Draw(im)
    for y in range(H): d.line((0,y,W,y),fill=(int(251-26*y/H),int(244-36*y/H),int(230-46*y/H)))
    return im
def wrap(d,text,f,maxw):
    lines=[]; cur=''
    for w in text.split():
        t=(cur+' '+w).strip()
        if d.textlength(t,font=f)>maxw: lines.append(cur); cur=w
        else: cur=t
    lines.append(cur); return lines
def headline(im,title,sub,color=GOLD,subcolor=(243,227,195),y=90,size=78,subsize=36,ornament=True):
    d=ImageDraw.Draw(im); f=font(SB,size); lines=wrap(d,title,f,980)
    for l in lines: d.text((W//2,y),l,font=f,fill=color,anchor='mt'); y+=size+8
    if ornament:
        y+=10; d.line((W//2-140,y,W//2-30,y),fill=SAFF,width=3); d.line((W//2+30,y,W//2+140,y),fill=SAFF,width=3); d.ellipse((W//2-10,y-10,W//2+10,y+10),fill=SAFF); y+=26
    fs=font(SN,subsize)
    for l in wrap(d,sub,fs,940): d.text((W//2,y),l,font=fs,fill=subcolor,anchor='mt'); y+=subsize+10
    return y
def badge(im,xy,text,fill=SAFF,fg=(30,10,5),size=30,pad=22):
    d=ImageDraw.Draw(im); f=font(SNB,size); tw=d.textlength(text,font=f); x,y=xy; h=size+pad
    d.rounded_rectangle((x,y,x+tw+2*pad,y+h),h//2,fill=fill); d.text((x+pad,y+h//2),text,font=f,fill=fg,anchor='lm'); return tw+2*pad
def pills(im,y,items,center=True):
    d=ImageDraw.Draw(im); f=font(SNB,28); pad=20; gap=16; h=28+2*pad
    ws=[d.textlength(t,font=f)+2*pad for t in items]; total=sum(ws)+gap*(len(items)-1); x=(W-total)//2 if center else 40
    for t,w in zip(items,ws):
        d.rounded_rectangle((x,y,x+w,y+h),h//2,fill=(0,0,0,0),outline=GOLD,width=2); d.text((x+w/2,y+h/2),t,font=f,fill=GOLD,anchor='mm'); x+=w+gap
def save(im,name): im.convert('RGB').save(O+name,quality=93)

# 1 Hero
im=bg_painting(127)
headline(im,'Wake up to the Ramayana','A new classical painting and its story on your phone, every single day',y=100)
ph=phone('wall-en-127.png',1400); x=(W-ph.width)//2; shadowed(im,ph,(x,440))
bw=ImageDraw.Draw(im).textlength('DAY 127 OF 310',font=font(SNB,30))+44; badge(im,(x+ph.width-int(bw)-40,470),'DAY 127 OF 310')
save(im,'screenshot-1-hero.png')

# 2 Follow the epic day by day
im=bg_cream()
headline(im,'Follow the whole epic, day by day','From Narada’s tale on day one to Rama’s last journey on day 310',color=MAROON,subcolor=(107,85,70),y=80,size=70)
order=(('wall-en-1.png','DAY 1',30,560,950,'l'),('wall-en-305.png','DAY 305',595,560,950,'r'),('wall-en-183.png','DAY 183',312,500,1010,'l'))
for src,day,x,y,h,side in order:
    ph=phone(src,h); shadowed(im,ph,(x,y))
    bw=int(ImageDraw.Draw(im).textlength(day,font=font(SNB,26)))+36
    badge(im,(x+20 if side=='l' else x+ph.width-bw-20,y+24),day,size=26,pad=18)
# kanda strip
d=ImageDraw.Draw(im); y0=1760; names=['Bala','Ayodhya','Aranya','Kishkindha','Sundara','Yuddha','Uttara']; segs=[47,47,54,30,43,69,20]
tot=sum(segs); x=60; f=font(SNB,20)
for n,s in zip(names,segs):
    w=int(960*s/tot); d.rounded_rectangle((x,y0,x+w-6,y0+22),8,fill=SAFF if n in('Bala','Aranya','Sundara','Uttara') else MAROON); d.text((x+(w-6)/2,y0+48),n,font=f,fill=INK,anchor='mm'); x+=w
d.text((W//2,y0-50),'Seven kandas, 310 incidents, in order',font=font(SN,28),fill=(107,85,70),anchor='mm')
save(im,'screenshot-2-epic.png')

# 3 Story
im=bg_painting(37,dark=0.78)
headline(im,'Not just a picture. The story too.','Kanda, title and a short tale sit under every painting, in plain words',y=90,size=72)
ph=phone('wall-en-37.png',1320); x=50; shadowed(im,ph,(x,450))
# zoom lens on story
src=Image.open(R+'wall-en-37.png').convert('RGB'); crop=src.crop((60,1535,1020,2150)); crop=crop.resize((700,int(700*crop.height/crop.width)),Image.LANCZOS)
card=rounded(crop,30); fr=Image.new('RGBA',(card.width+12,card.height+12),(0,0,0,0)); ImageDraw.Draw(fr).rounded_rectangle((0,0,fr.width-1,fr.height-1),36,fill=GOLD); fr.alpha_composite(card,(6,6))
shadowed(im,fr,(W-fr.width-40,H-fr.height-110),blur=30,alpha=0.85)
save(im,'screenshot-3-story.png')

# 4 Swipe
im=bg_cream()
headline(im,'Swipe to read ahead or look back','Browse all 310 incidents and set any one as your wallpaper',color=MAROON,subcolor=(107,85,70),y=90,size=70)
ph=phone('screen-en-top.png',1420); x=(W-ph.width)//2; shadowed(im,ph,(x,440))
d=ImageDraw.Draw(im)
for cx,dirn in ((110,-1),(970,1)):
    cy=1150; d.ellipse((cx-66,cy-66,cx+66,cy+66),fill=SAFF); d.polygon([(cx+dirn*30,cy),(cx-dirn*18,cy-34),(cx-dirn*18,cy+34)],fill=(255,255,255))
save(im,'screenshot-4-swipe.png')

# 5 Languages
im=bg_painting(250,dark=0.8)
headline(im,'Read it in your language','English · മലയാളം · தமிழ் · తెలుగు · ಕನ್ನಡ · हिन्दी',y=100,size=76) if False else headline(im,'Read it in your language','English, Malayalam, Tamil, Telugu, Kannada and Hindi. It follows your phone, or you choose.',y=100,size=76)
fan=(('wall-kn-95.png',-14,-40,560,1150),('wall-te-278.png',-7,170,500,1200),('wall-ml-127.png',0,330,470,1250),('wall-ta-37.png',7,520,500,1200),('wall-hi-250.png',14,720,560,1150))
for src,ang,x,y,h in fan:
    ph=phone(src,h).rotate(ang,expand=True,resample=Image.BICUBIC); shadowed(im,ph,(x,y),blur=26)
save(im,'screenshot-5-languages.png')

# 6 Settings
im=bg_cream()
headline(im,'Home screen, lock screen or both','Changes on its own after midnight. Hide the story text if you just want the art.',color=MAROON,subcolor=(107,85,70),y=90,size=70)
ph=phone('screen-en-settings.png',1420); x=(W-ph.width)//2; shadowed(im,ph,(x,460))
save(im,'screenshot-6-settings.png')

# 7 Paintings mosaic + promises
im=Image.new('RGBA',(W,H),MAROON)
headline(im,'310 classical paintings, all inside the app','Raja Ravi Varma, the Mewar Ramayana, Pahari and Mughal folios and more, each credited to its source',y=80,size=66,subsize=32)
ids=[1,13,36,43,55,63,80,97,121,127,131,151,155,171,182,199,215,242,256,271,278,287,299,305]
cw,ch=330,330; y0=430
for k,i in enumerate(ids[:12]):
    p=cover(Image.open(P+f'{i}.jpg').convert('RGB'),cw,ch); im.alpha_composite(rounded(p,18),(30+(k%3)*345,y0+(k//3)*300))
pills(im,y0+4*300+30,['No ads','No account','Works offline','Free'])
save(im,'screenshot-7-paintings.png')
print('ok')
