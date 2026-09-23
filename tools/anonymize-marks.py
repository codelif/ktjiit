#!/usr/bin/env python3
# strips identity out of a portal marks pdf so it can live in the repo as a fixture.
# name and enrollment become fakes, the jiit logo/watermark become 1x1 gray pixels.
# usage: anonymize-marks.py in.pdf out.pdf [seed]
import hashlib
import re
import sys
import zlib

import pikepdf
from pikepdf import Name


def fake_digits(n, seed):
    return ("99" + str(int(hashlib.sha256(seed.encode()).hexdigest(), 16)))[:n]


def scrub_text(data: bytes, seed: str) -> bytes:
    def name(_):
        return b"(Name: TEST STUDENT " + seed.upper().encode() + b")"

    def enroll(m):
        return b"(Enrollment No: " + fake_digits(len(m.group(1)), seed).encode() + b")"

    data = re.sub(rb"\(Name:\s*[^)]*\)", name, data)
    data = re.sub(rb"\(Enrollment No:\s*([^)]*)\)", enroll, data)
    return data


def main():
    src, dst = sys.argv[1], sys.argv[2]
    seed = sys.argv[3] if len(sys.argv) > 3 else "A"
    pdf = pikepdf.open(src)
    for page in pdf.pages:
        contents = page.obj.Contents
        streams = list(contents) if isinstance(contents, pikepdf.Array) else [contents]
        for s in streams:
            s.write(zlib.compress(scrub_text(s.read_bytes(), seed)), filter=Name.FlateDecode)
    for obj in pdf.objects:
        if isinstance(obj, pikepdf.Stream) and obj.get("/Subtype") == Name.Image:
            for k in ("/Filter", "/DecodeParms", "/SMask", "/Mask", "/ColorTransform", "/Intent", "/Decode"):
                if k in obj:
                    del obj[k]
            obj.Width, obj.Height, obj.BitsPerComponent = 1, 1, 8
            obj.ColorSpace = Name.DeviceGray
            obj.write(b"\x80")
    pdf.docinfo = pdf.make_indirect(pikepdf.Dictionary())
    pdf.save(dst, object_stream_mode=pikepdf.ObjectStreamMode.disable, compress_streams=True, linearize=False)


if __name__ == "__main__":
    main()
