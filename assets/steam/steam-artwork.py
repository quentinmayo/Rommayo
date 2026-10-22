#!/usr/bin/env python3
"""Install only Mayo's own Steam shortcut/art. Steam must be fully exited."""
import argparse
import os
from pathlib import Path
import shutil
import struct
import tempfile
import time
import zlib


def decode(data):
    pos = 0

    def string():
        nonlocal pos
        end = data.index(b'\0', pos)
        value = data[pos:end].decode('utf-8')
        pos = end + 1
        return value

    def obj(depth=0):
        nonlocal pos
        if depth > 32:
            raise ValueError('Too many nested shortcut objects')
        entries = []
        while pos < len(data):
            kind = data[pos]
            pos += 1
            if kind == 8:
                return entries
            key = string()
            if kind == 0:
                value = obj(depth + 1)
            elif kind == 1:
                value = string()
            elif kind in (2, 3, 4, 6, 7, 10):
                size = 8 if kind in (7, 10) else 4
                value = data[pos:pos + size]
                if len(value) != size:
                    raise ValueError('Truncated shortcut value')
                pos += size
            else:
                raise ValueError('Unsupported shortcut value; original file was not changed')
            entries.append([kind, key, value])
        raise ValueError('Truncated shortcuts file')

    result = obj()
    if pos != len(data):
        raise ValueError('Unexpected trailing shortcut data')
    return result


def encode(entries):
    output = bytearray()
    for kind, key, value in entries:
        output.extend(bytes([kind]) + key.encode() + b'\0')
        output.extend(encode(value) if kind == 0 else value.encode() + b'\0' if kind == 1 else value)
    return bytes(output) + b'\x08'


def field(entries, name):
    return next((e for e in entries if e[1].lower() == name.lower()), None)


def set_field(entries, kind, name, value):
    existing = field(entries, name)
    if existing:
        existing[0], existing[2] = kind, value
    else:
        entries.append([kind, name, value])


def steam_running():
    for proc in Path('/proc').glob('[0-9]*'):
        try:
            if proc.stat().st_uid == os.getuid() and (proc / 'comm').read_text().strip() in ('steam', 'steamwebhelper'):
                return True
        except OSError:
            pass
    return False


def atomic_write(target, data):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and target.read_bytes() == data:
        return
    if target.exists():
        shutil.copy2(target, target.with_name(target.name + '.mayo-backup-' + str(time.time_ns())))
    fd, temporary = tempfile.mkstemp(prefix='.mayo-', dir=target.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data)
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def install(root, name, slug, prefix, assets):
    if steam_running():
        raise ValueError('Exit Steam first (Steam menu → Exit), then run this command again. No shortcuts were changed.')
    profiles = sorted((root / 'userdata').glob('[0-9]*/config'))
    if not profiles:
        raise ValueError('No Steam profile found. Sign into Steam once, exit Steam, then retry.')
    launcher = prefix / 'bin' / slug
    icon = assets / 'icon.png'
    for config in profiles:
        target = config / 'shortcuts.vdf'
        original = target.read_bytes() if target.exists() else encode([[0, 'shortcuts', []]])
        document = decode(original)
        shortcuts = field(document, 'shortcuts')
        if not shortcuts or shortcuts[0] != 0:
            raise ValueError('Unexpected shortcuts layout. Original file was not changed.')
        matches = []
        for entry in shortcuts[2]:
            if entry[0] != 0:
                continue
            exe = field(entry[2], 'exe')
            appname = field(entry[2], 'appname')
            if exe and exe[0] == 1 and exe[2].strip('"') == str(launcher):
                matches.append(entry[2])
            elif appname and appname[2] == name and exe and exe[0] == 1 and exe[2].strip('"') == str(prefix / 'share' / slug / (name + '.AppImage')):
                matches.append(entry[2])
        if not matches:
            exe = '"' + str(launcher) + '"'
            appid = zlib.crc32((exe + name).encode()) | 0x80000000
            entry = [[2, 'appid', struct.pack('<I', appid)], [1, 'AppName', name], [1, 'Exe', exe], [1, 'StartDir', '"' + str(launcher.parent) + '"'], [1, 'LaunchOptions', ''], [2, 'IsHidden', struct.pack('<I', 0)], [2, 'AllowDesktopConfig', struct.pack('<I', 1)], [2, 'AllowOverlay', struct.pack('<I', 1)], [0, 'tags', []]]
            number = max([int(e[1]) for e in shortcuts[2] if e[1].isdigit()] + [-1]) + 1
            shortcuts[2].append([0, str(number), entry])
            matches.append(entry)
        # Validate before writing anything in this profile. Existing app IDs and controls stay intact.
        ids = []
        for entry in matches:
            identifier = field(entry, 'appid')
            if not identifier or identifier[0] != 2:
                raise ValueError('Shortcut has no app ID. Original file was not changed.')
            ids.append(struct.unpack('<I', identifier[2])[0])
            set_field(entry, 1, 'icon', str(icon))
        atomic_write(target, encode(document))
        for identifier in ids:
            for source, suffix in [('portrait.png', 'p.png'), ('landscape.png', '.png'), ('hero.png', '_hero.png'), ('logo.png', '_logo.png'), ('icon.png', '_icon.png')]:
                atomic_write(config / 'grid' / (str(identifier) + suffix), (assets / source).read_bytes())
        print(name + ': shortcut and original artwork installed for Steam profile ' + config.parent.name)
    print('Reopen Steam. Your shortcut is under Library → Non-Steam.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True, choices=['Rommayo', 'Emulatormayo'])
    parser.add_argument('--prefix', type=Path, default=Path.home() / '.local')
    parser.add_argument('--steam-root', type=Path, default=Path.home() / '.local/share/Steam')
    args = parser.parse_args()
    root = args.steam_root
    if not root.exists() and (Path.home() / '.steam/steam').exists():
        root = Path.home() / '.steam/steam'
    try:
        install(root, args.name, args.name.lower(), args.prefix, Path(__file__).resolve().parent)
    except (OSError, ValueError, UnicodeError, struct.error) as error:
        parser.exit(1, str(error) + '\n')


if __name__ == '__main__':
    main()
