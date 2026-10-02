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
    fieldDocuments=extra['fieldDocuments'],
    phrases=rows, documents=[dict(id='home-message', title='오늘, 구원이 이 집에 이르렀습니다',
    source='사용자 제공 DATA/가정심방 말씀.hwpx', blocks=[x for x in lines if x])])
# 예배 순서도 선교 일정처럼 앱에 넣지 않고, 앱의 현장 자료 화면에서 사용자가 직접 등록한다.
for name in ('schedule.pdf', 'service-order.jpg'):
    (assets / name).unlink(missing_ok=True)
# 선교 일정 PDF는 개인 사정이 있어 앱에 넣지 않는다. 앱의 현장 자료 화면에서 사용자가 직접 등록한다.
content['files'] = {}  # 앱에 내장하는 필수 파일 없음(해시 점검 대상 없음)
errors = validate(content)
if errors:
    raise ValueError(errors)
(assets / 'content.json').write_text(json.dumps(content,ensure_ascii=False,indent=2), encoding='utf-8')
print(f'Prepared {len(rows)} phrases, {len(lines)} source text blocks. Draft only.')
