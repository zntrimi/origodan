#!/usr/bin/env python3
"""Build the offline search index from Unicode Emoji 17.0 / CLDR 48.

Run from the repository root. Sources and output are licensed under Unicode-3.0.
Only fully qualified emoji without skin-tone variants are indexed, so a search
does not bury the base emoji under dozens of near-identical results.
"""
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

CLDR = 'https://raw.githubusercontent.com/unicode-org/cldr/release-48/common'
EMOJI = 'https://www.unicode.org/Public/17.0.0/emoji/emoji-test.txt'


def download(url):
    with urllib.request.urlopen(url) as response:
        return response.read()


def annotations(language):
    result = {}
    for directory in ('annotations', 'annotationsDerived'):
        root = ET.fromstring(download(f'{CLDR}/{directory}/{language}.xml'))
        for element in root.findall('.//annotation'):
            key = element.attrib['cp'].replace('\ufe0f', '')
            text = element.text or ''
            if text and text not in ('↑↑↑', '∅∅∅'):
                result.setdefault(key, []).extend(text.split(' | '))
    return result


def main():
    english, japanese = annotations('en'), annotations('ja')
    rows = []
    for line in download(EMOJI).decode('utf-8').splitlines():
        if '; fully-qualified' not in line:
            continue
        points = [int(point, 16) for point in line.split(';')[0].split()]
        if any(0x1f3fb <= point <= 0x1f3ff for point in points):
            continue
        emoji = ''.join(map(chr, points))
        key = emoji.replace('\ufe0f', '')
        # emoji-test also supplies an English name for newly added emoji.
        name = line.split('#', 1)[1].strip().split(' ', 2)[2]
        terms = list(dict.fromkeys(english.get(key, []) + japanese.get(key, []) + [name]))
        rows.append(emoji + '\t' + '\t'.join(terms))
    target = Path('app/src/main/assets/emoji_search/keywords.tsv')
    target.write_text('\n'.join(rows) + '\n', encoding='utf-8')
    print(f'{len(rows)} emoji, {target.stat().st_size} bytes')


if __name__ == '__main__':
    main()
