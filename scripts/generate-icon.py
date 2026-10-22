"""Generate the original Rommayo cartridge icon with Python's standard library."""
import struct
import zlib
from pathlib import Path

size = 512
pixels = bytearray()
for y in range(size):
    pixels.append(0)
    for x in range(size):
        color = (23, 29, 37)
        if 112 <= x < 400 and 82 <= y < 430:
            color = (102, 192, 244)
        if 155 <= x < 357 and 123 <= y < 143:
            color = (23, 29, 37)
        if 212 <= x <= 321 and abs(y - 254) <= (321 - x) * 69 / 109:
            color = (23, 29, 37)
        if 191 <= x < 321 and 392 <= y < 430:
            color = (23, 29, 37)
        pixels.extend(color)

def chunk(name, data):
    return struct.pack('!I', len(data)) + name + data + struct.pack('!I', zlib.crc32(name + data))

output = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!IIBBBBB', size, size, 8, 2, 0, 0, 0))
output += chunk(b'IDAT', zlib.compress(pixels)) + chunk(b'IEND', b'')
Path(__file__).resolve().parents[1].joinpath('assets/icon.png').write_bytes(output)
