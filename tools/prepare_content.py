"""Create development assets without changing DATA. No automatic review approval."""
import csv
import json
from pathlib import Path
import shutil
import zipfile
import xml.etree.ElementTree as ET
from validate_content import validate

root = Path(__file__).resolve().parents[1]
assets = root / 'app/src/main/assets'
assets.mkdir(parents=True, exist_ok=True)
with (root / 'content/phrases.tsv').open(encoding='utf-8', newline='') as stream:
    rows = list(csv.DictReader(stream, delimiter='\t'))
for i, row in enumerate(rows):
    row['id'] = f'phrase-{i+1:03}'
with zipfile.ZipFile(root / 'DATA/가정심방 말씀.hwpx') as archive:
    lines = [''.join(n.itertext()).strip() for name in archive.namelist()
             if name.startswith('Contents/section') and name.endswith('.xml')
             for n in ET.fromstring(archive.read(name)).iter() if n.tag.endswith('}t')]
extra = json.loads((root / 'content/extra.json').read_text(encoding='utf-8'))
content = dict(version='0.1.0-draft', reviewStatus='draft', licenseStatus='original',
    source='표현 참고: Peace Corps Tagalog Language Packet (https://eric.ed.gov/?id=ED402768). 사역 문장·번역·독음은 작성 초안이며 현지 미검수.',
    worship=extra['worship'], songs=extra['songs'], gospel=extra['gospel'], contacts=extra['contacts'],
    fieldDocuments=extra['fieldDocuments'], scheduleSanitized=False,
    phrases=rows, documents=[dict(id='home-message', title='오늘, 구원이 이 집에 이르렀습니다',
    source='사용자 제공 DATA/가정심방 말씀.hwpx', blocks=[x for x in lines if x])])
shutil.copyfile(root / 'DATA/가정심방 예배순서.jpg', assets / 'service-order.jpg')
shutil.copyfile(root / 'DATA/선교 일정.pdf', assets / 'schedule.pdf')  # 개발용 원본. 릴리스 전 개인 사정 제거 사본으로 교체
import hashlib
content['files'] = {n: hashlib.sha256((assets / n).read_bytes()).hexdigest() for n in ('service-order.jpg', 'schedule.pdf')}
errors = validate(content)
if errors:
    raise ValueError(errors)
(assets / 'content.json').write_text(json.dumps(content,ensure_ascii=False,indent=2), encoding='utf-8')
print(f'Prepared {len(rows)} phrases, {len(lines)} source text blocks and service-order JPG. Draft only.')
