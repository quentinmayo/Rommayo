"""Exercise Steam file updates without touching a real Steam profile."""
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'assets/steam' if (ROOT / 'assets').exists() else ROOT / 'desktop/assets/steam'
spec = importlib.util.spec_from_file_location('artwork', ASSETS / 'steam-artwork.py')
art = importlib.util.module_from_spec(spec)
spec.loader.exec_module(art)


class ArtworkTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.config = self.root / 'userdata/123/config'
        self.config.mkdir(parents=True)
        self.prefix = self.root / 'my local'
        self.target = self.config / 'shortcuts.vdf'

    def install(self):
        art.install(self.root, 'Rommayo', 'rommayo', self.prefix, ASSETS)

    @patch.object(art, 'steam_running', return_value=False)
    def test_preserves_existing_appid_controls_other_apps_and_is_idempotent(self, _):
        entry = [[2, 'appid', struct.pack('<I', 4070847932)], [1, 'AppName', 'Rommayo'], [1, 'Exe', '"' + str(self.prefix / 'bin/rommayo') + '"'], [1, 'LaunchOptions', '--custom'], [0, 'tags', [[1, '0', 'Favorites']]]]
        other = [0, '1', [[1, 'AppName', 'My other game'], [2, 'appid', struct.pack('<I', 1234)]]]
        original = art.encode([[0, 'shortcuts', [[0, '0', entry], other]]])
        self.target.write_bytes(original)
        self.install()
        saved = self.target.read_bytes()
        entries = art.field(art.decode(saved), 'shortcuts')[2]
        self.assertEqual(len(entries), 2)
        self.assertEqual(entries[1], other)
        self.assertEqual(art.field(entries[0][2], 'appid')[2], struct.pack('<I', 4070847932))
        self.assertEqual(art.field(entries[0][2], 'LaunchOptions')[2], '--custom')
        self.assertEqual((self.config / 'grid/4070847932p.png').read_bytes(), (ASSETS / 'portrait.png').read_bytes())
        self.assertEqual(next(self.config.glob('shortcuts.vdf.mayo-backup-*')).read_bytes(), original)
        self.install()
        self.assertEqual(self.target.read_bytes(), saved)
        self.assertEqual(len(list(self.config.glob('shortcuts.vdf.mayo-backup-*'))), 1)

    @patch.object(art, 'steam_running', return_value=False)
    def test_adds_missing_shortcut_and_backs_up_existing_art(self, _):
        self.install()
        entry = art.field(art.decode(self.target.read_bytes()), 'shortcuts')[2][0][2]
        identifier = struct.unpack('<I', art.field(entry, 'appid')[2])[0]
        hero = self.config / ('grid/' + str(identifier) + '_hero.png')
        hero.write_bytes(b'custom-art-fixture')
        self.install()
        self.assertEqual(next(hero.parent.glob(hero.name + '.mayo-backup-*')).read_bytes(), b'custom-art-fixture')
        self.assertEqual(len(art.field(art.decode(self.target.read_bytes()), 'shortcuts')[2]), 1)

    @patch.object(art, 'steam_running', return_value=True)
    def test_refuses_while_steam_is_running(self, _):
        self.target.write_bytes(b'untouched')
        with self.assertRaisesRegex(ValueError, 'Exit Steam first'):
            self.install()
        self.assertEqual(self.target.read_bytes(), b'untouched')
        self.assertFalse((self.config / 'grid').exists())

    @patch.object(art, 'steam_running', return_value=False)
    def test_malformed_file_fails_without_changes(self, _):
        self.target.write_bytes(b'\x00shortcuts\x00\x05unsupported\x00')
        original = self.target.read_bytes()
        with self.assertRaises(ValueError):
            self.install()
        self.assertEqual(self.target.read_bytes(), original)
        self.assertFalse((self.config / 'grid').exists())


if __name__ == '__main__':
    unittest.main()
