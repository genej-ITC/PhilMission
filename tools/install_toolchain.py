"""Install project-local verified JDK and Gradle; download Google's SDK tools."""
import hashlib
import json
from pathlib import Path
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
import subprocess

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / '.tools'
DEST.mkdir(exist_ok=True)

def get(url):
    return subprocess.check_output(['curl.exe', '-fsSL', '--retry', '2', '--max-time', '120', url])

def download(url, filename, digest, algorithm='sha256'):
    path = DEST / filename
    if not path.exists():
        print('Downloading', filename, flush=True)
        subprocess.run(['curl.exe', '-fL', '--retry', '2', '--max-time', '600', url, '-o', str(path)], check=True)
    actual = hashlib.new(algorithm, path.read_bytes()).hexdigest()
    if actual != digest.strip():
        raise ValueError(f'Checksum mismatch: {filename}; remove the incomplete archive and retry')
    return path

def main():
    assets = json.loads(get('https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'))
    package = assets[0]['binary']['package']
    jdk = download(package['link'], package['name'], package['checksum'])
    if not list(DEST.glob('jdk-*/bin/java.exe')):
        with zipfile.ZipFile(jdk) as archive:
            archive.extractall(DEST)
    base = 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip'
    gradle = download(base, 'gradle-8.11.1-bin.zip', get(base + '.sha256').decode())
    if not (DEST / 'gradle-8.11.1/bin/gradle.bat').exists():
        with zipfile.ZipFile(gradle) as archive:
            archive.extractall(DEST)
    repo = ET.fromstring(get('https://dl.google.com/android/repository/repository2-1.xml'))
    packages = [p for p in repo if p.tag.endswith('remotePackage') and p.attrib['path'].startswith('cmdline-tools;')]
    for p in packages:
        for archive in p.findall('./archives/archive'):
            if archive.findtext('host-os') == 'windows':
                url = archive.findtext('complete/url')
                if '13114758' in url:
                    item = (url, archive.findtext('complete/checksum'))
                    break
        else:
            continue
        break
    else:
        raise RuntimeError('Pinned SDK command line tools 13114758 not found in official repository')
    sdkzip = download('https://dl.google.com/android/repository/' + item[0], 'sdk-tools.zip', item[1], 'sha1')
    sdk = DEST / 'android-sdk'
    target = sdk / 'cmdline-tools' / 'latest'
    if not (target / 'bin/sdkmanager.bat').exists():
        with zipfile.ZipFile(sdkzip) as archive:
            for member in archive.infolist():
                relative = Path(member.filename).relative_to('cmdline-tools')
                output = target / relative
                if member.is_dir():
                    output.mkdir(parents=True, exist_ok=True)
                else:
                    output.parent.mkdir(parents=True, exist_ok=True)
                    output.write_bytes(archive.read(member))
    (ROOT / 'local.properties').write_text('sdk.dir=' + sdk.as_posix() + '\n', encoding='utf-8')
    (DEST / 'toolchain.json').write_text(json.dumps({'jdk':package,'gradle':'8.11.1','sdkTools':item}, indent=2), encoding='utf-8')
    print('Toolchain downloaded and checksums verified.', flush=True)

if __name__ == '__main__':
    main()
