# Uploads a release to Google Play through the Play Developer API.
#   PLAY_KEY=service-account.json AAB=build-local/bhagavatham-wallpapers.aab MAPPING=build-local/mapping.txt \
#   python3 tools/play_publish.py all|listing|bundle|whoami
# 'bundle' uploads the .aab and its R8 mapping file and sets a DRAFT release on the production track;
# 'listing' uploads store text, icon, feature graphic and the screenshots in store/. Needs: pip install pyjwt cryptography
import json, time, sys, re, os, glob, jwt, urllib.request, urllib.error, urllib.parse
K=json.load(open(os.environ['PLAY_KEY'])); PKG='com.calecutech.bhagavatham'
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
md=open('store/listing.md').read()
full=md.split('**Full description:**')[1].split('**Category:**')[0].strip()
short=re.search(r'\*\*Short description\*\* \(80 max\):\n(.+)',md).group(1).strip()
if cmd in('all','listing'):
    print('listing',call('PUT',E+'/listings/en-US',{'language':'en-US','title':'Bhagavatham Wallpapers','shortDescription':short,'fullDescription':full,'video':''}).get('title'))
    S='store/'
    imgs=[('icon','icon-512.png'),('featureGraphic','feature-graphic-1024x500.png')]+[('phoneScreenshots',f) for f in sorted(glob.glob(S+'screenshot-*.png'))]
    for t,_ in {'icon':0,'featureGraphic':0,'phoneScreenshots':0}.items(): call('DELETE',E+f'/listings/en-US/{t}')
    for t,f in imgs:
        p=f if f.startswith('/') else S+f
        r=call('POST',EU+f'/listings/en-US/{t}?uploadType=media',open(p,'rb').read(),'image/png',raw=True); print(t,p.split('/')[-1],r.get('image',{}).get('id'))
    print('details',call('PATCH',E+'/details',{'contactEmail':'info@calecutech.com','contactWebsite':'https://calecutech.com/bhagavatham/','defaultLanguage':'en-US'}))
if cmd in('all','bundle'):
    aab=open(os.environ.get('AAB','build-local/bhagavatham-wallpapers.aab'),'rb').read()
    b=call('POST',EU+'/bundles?uploadType=media',aab,'application/octet-stream',raw=True); print('bundle',b)
    vc=b['versionCode']
    mp=os.environ.get('MAPPING','build-local/mapping.txt')
    if os.path.exists(mp):
        print('mapping',call('POST',EU+f'/apks/{vc}/deobfuscationFiles/proguard?uploadType=media',open(mp,'rb').read(),'application/octet-stream',raw=True))
    vn=re.search(r'VERSION_NAME=(\S+)',open('build-local.sh').read()).group(1)
    notes=os.environ.get('NOTES','First release: a Bhagavatam painting and its story as your wallpaper every day. 318 incidents across the twelve skandhas, six languages, all paintings bundled.')
    print('track',call('PUT',E+'/tracks/production',{'track':'production','releases':[{'name':f'{vn} ({vc})','versionCodes':[str(vc)],'status':'draft','releaseNotes':[{'language':'en-US','text':notes}]}]}))
print('validate',call('POST',E+':validate'))
print('commit',call('POST',E+':commit'))
