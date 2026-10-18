import json, time, sys, re, jwt, urllib.request, urllib.error, urllib.parse
K=json.load(open('__import__('os').environ['PLAY_KEY']')); PKG='com.calecutech.ramayanam'
BASE=f'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG}'
UP=f'https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PKG}'
now=int(time.time())
a=jwt.encode({'iss':K['client_email'],'scope':'https://www.googleapis.com/auth/androidpublisher','aud':K['token_uri'],'iat':now,'exp':now+3600},K['private_key'],algorithm='RS256')
r=urllib.request.urlopen(urllib.request.Request(K['token_uri'],data=urllib.parse.urlencode({'grant_type':'urn:ietf:params:oauth:grant-type:jwt-bearer','assertion':a}).encode()))
TOK=json.load(r)['access_token']
def call(method,url,body=None,ctype='application/json',raw=False):
    data=body if raw else (json.dumps(body).encode() if body is not None else None)
    req=urllib.request.Request(url,data=data,method=method,headers={'Authorization':'Bearer '+TOK,'Content-Type':ctype})
    try:
        with urllib.request.urlopen(req,timeout=600) as resp:
            t=resp.read(); return json.loads(t) if t else {}
    except urllib.error.HTTPError as e:
        print('ERR',method,url,e.code,e.read().decode()[:800]); raise
cmd=sys.argv[1]
if cmd=='whoami':
    e=call('POST',BASE+'/edits'); print('edit ok',e['id']); call('DELETE',BASE+f"/edits/{e['id']}")
    sys.exit()
e=call('POST',BASE+'/edits'); EID=e['id']; E=BASE+f'/edits/{EID}'; EU=UP+f'/edits/{EID}'; print('edit',EID)
md=open('/home/claude/ramayanawallpaper/store/listing.md').read()
full=md.split('**Full description:**')[1].split('**Category:**')[0].strip()
short=re.search(r'\*\*Short description\*\* \(80 max\):\n(.+)',md).group(1).strip()
if cmd in('all','listing'):
    print('listing',call('PUT',E+'/listings/en-US',{'language':'en-US','title':'Ramayanam Wallpapers','shortDescription':short,'fullDescription':full,'video':''}).get('title'))
    S='/home/claude/ramayanawallpaper/store/'
    imgs=[('icon','icon-512.png'),('featureGraphic','feature-graphic-1024x500.png')]+[('phoneScreenshots',f) for f in sorted(__import__('glob').glob(S+'screenshot-*.png'))]
    for t,_ in {'icon':0,'featureGraphic':0,'phoneScreenshots':0}.items(): call('DELETE',E+f'/listings/en-US/{t}')
    for t,f in imgs:
        p=f if f.startswith('/') else S+f
        r=call('POST',EU+f'/listings/en-US/{t}?uploadType=media',open(p,'rb').read(),'image/png',raw=True); print(t,p.split('/')[-1],r.get('image',{}).get('id'))
    print('details',call('PATCH',E+'/details',{'contactEmail':'info@calecutech.com','contactWebsite':'https://calecutech.com/ramayanam/','defaultLanguage':'en-US'}))
if cmd in('all','bundle'):
    aab=open('/mnt/project-files/ramayana-wallpaper/play-release/ramayanam-1.7.aab','rb').read()
    b=call('POST',EU+'/bundles?uploadType=media',aab,'application/octet-stream',raw=True); print('bundle',b)
    vc=b['versionCode']
    print('track',call('PUT',E+'/tracks/production',{'track':'production','releases':[{'name':'1.7 (8)','versionCodes':[str(vc)],'status':'draft','releaseNotes':[{'language':'en-US','text':'A new Ramayana painting and its story as your wallpaper every day. 310 incidents, six languages, all paintings bundled.'}]}]}))
print('validate',call('POST',E+':validate'))
print('commit',call('POST',E+':commit'))
