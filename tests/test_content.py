import copy
import unittest
from tools.validate_content import validate


def fixture():
    rows = []
    for category, count in [('greeting',10), ('children',15), ('meal',10), ('visit',10), ('emergency',5)]:
        for i in range(count):
            rows.append(dict(id=f'{category}-{i}',category=category,tl='Kumusta?',en='Hello',ko='안녕하세요',pronunciationTl='꾸무스따'))
    return dict(version='0.1.0',reviewStatus='draft',phrases=rows,documents=[])


class ContentTests(unittest.TestCase):
    def test_valid_draft(self):
        self.assertEqual([], validate(fixture()))

    def test_duplicate_ids(self):
        data=fixture(); data['phrases'][1]['id']=data['phrases'][0]['id']
        self.assertTrue(any('duplicate' in e for e in validate(data)))

    def test_missing_translation(self):
        data=fixture(); data['phrases'][0]['en']=' '
        self.assertTrue(any('en' in e for e in validate(data)))

    def test_wrong_distribution(self):
        data=fixture(); data['phrases'][0]['category']='children'
        self.assertTrue(any('category' in e for e in validate(data)))

    def test_draft_cannot_release(self):
        self.assertTrue(any('review' in e for e in validate(fixture(),release=True)))

if __name__ == '__main__':
    unittest.main()
