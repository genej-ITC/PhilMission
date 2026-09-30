import argparse
from collections import Counter
import json
from pathlib import Path

CATEGORIES = {'greeting':10, 'children':15, 'meal':10, 'visit':10, 'emergency':5}

def validate(data, release=False):
    errors = []
    rows = data.get('phrases', [])
    if len(rows) != 50:
        errors.append('expected 50 phrases')
    seen = set()
    for row in rows:
        ident = row.get('id')
        if not ident or ident in seen:
            errors.append(f'duplicate or empty id: {ident}')
        seen.add(ident)
        for field in ('tl','en','ko','pronunciationTl'):
            if not isinstance(row.get(field), str) or not row[field].strip():
                errors.append(f'{ident}: missing {field}')
    if Counter(row.get('category') for row in rows) != Counter(CATEGORIES):
        errors.append('category distribution must be 10/15/10/10/5')
    for doc in data.get('worship', []):
        for sentence in doc.get('sentences', []):
            for field in ('tl','en','ko','pron'):
                if not str(sentence.get(field,'')).strip():
                    errors.append(f"{sentence.get('id')}: missing {field}")
    for song in data.get('songs', []):
        if not song.get('lines'):
            errors.append(f"{song.get('id')}: no lyric lines")
    if release:
        if data.get('reviewStatus') != 'approved':
            errors.append('review not approved')
        if data.get('licenseStatus') not in ('approved','original'):
            errors.append('license not approved')
        for group, count in [('worship',4), ('songs',2), ('fieldDocuments',3)]:
            if len(data.get(group,[])) != count:
                errors.append(f'release requires {count} {group}')
        if len(data.get('gospel',{}).get('cards',[])) < 1:
            errors.append('release requires gospel cards')
        if not data.get('scheduleSanitized'):
            errors.append('schedule PDF must be replaced with a sanitized copy')
    return errors

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--release',action='store_true')
    parser.add_argument('path',nargs='?',default='app/src/main/assets/content.json')
    args=parser.parse_args()
    result=validate(json.loads(Path(args.path).read_text(encoding='utf-8')),args.release)
    print('\n'.join(result) if result else 'Content validation passed (draft structure only).')
    raise SystemExit(bool(result))
