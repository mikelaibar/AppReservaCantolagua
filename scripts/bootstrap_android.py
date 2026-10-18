#!/usr/bin/env python3
"""Download pinned Android build tools; verify against Google's repository metadata."""
import os, pathlib, urllib.request, xml.etree.ElementTree as ET, hashlib, zipfile, shutil
ROOT=pathlib.Path(os.environ.get('CANTOLAGUA_TOOLS','/workspace/.tools'))
SDK=ROOT/'android-sdk';ROOT.mkdir(parents=True,exist_ok=True)
def download(url,path):
    if not path.exists():
        temp=path.with_suffix(path.suffix+'.tmp')
        urllib.request.urlretrieve(url,temp);temp.replace(path)
def archive(name,package,parent,destination):
    metadata=ROOT/'android-repository.xml'
    if not metadata.exists():download('https://dl.google.com/android/repository/repository2-1.xml',metadata)
    repository=ET.parse(metadata).getroot();expected=None
    for p in repository.findall('remotePackage'):
        if p.get('path')==package:
            for a in p.findall('archives/archive/complete'):
                if a.findtext('url')==name:expected=a.findtext('checksum')
    if not expected:raise RuntimeError('Package missing from official repository: '+name)
    path=ROOT/name;download('https://dl.google.com/android/repository/'+name,path)
    if hashlib.sha1(path.read_bytes()).hexdigest()!=expected:raise RuntimeError('Checksum mismatch: '+name)
    target=SDK/destination
    if not (target/'source.properties').exists():
        stage=ROOT/'extract';shutil.rmtree(stage,ignore_errors=True);stage.mkdir()
        with zipfile.ZipFile(path) as z:
            z.extractall(stage)
            for info in z.infolist():
                mode=info.external_attr>>16
                if mode:os.chmod(stage/info.filename,mode)
        children=list(stage.iterdir());target.parent.mkdir(parents=True,exist_ok=True)
        if len(children)!=1:raise RuntimeError('Unexpected SDK archive layout')
        if target.exists():shutil.rmtree(target)
        shutil.move(str(children[0]),target);shutil.rmtree(stage)
    print('Verified '+package)
archive('platform-35_r02.zip','platforms;android-35','platforms','platforms/android-35')
archive('build-tools_r35_linux.zip','build-tools;35.0.0','build-tools','build-tools/35.0.0')
for name in ['aapt2','zipalign','apksigner','dexdump']:(SDK/'build-tools/35.0.0'/name).chmod(0o755)
for artifact,version in [('jsoup','1.21.2'),('json','20240303')]:
    group='org/jsoup' if artifact=='jsoup' else 'org/json'
    url=f'https://repo.maven.apache.org/maven2/{group}/{artifact}/{version}/{artifact}-{version}.jar'
    algorithm='sha256' if artifact=='jsoup' else 'sha1'
    path=ROOT/(artifact+'.jar');checksum=ROOT/(artifact+'.'+algorithm);download(url,path);download(url+'.'+algorithm,checksum)
    if hashlib.new(algorithm,path.read_bytes()).hexdigest()!=checksum.read_text().strip().split()[0]:raise RuntimeError('Checksum mismatch: '+artifact)
    print('Verified '+artifact+' '+version)
print('Android SDK:',SDK)
