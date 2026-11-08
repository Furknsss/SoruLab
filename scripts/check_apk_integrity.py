#!/usr/bin/env python3
"""Check APK bytes before delivery and again after a file-transfer round trip."""
import argparse
import hashlib
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--expected-size", type=int)
    parser.add_argument("--expected-sha256")
    args = parser.parse_args()
    size = args.apk.stat().st_size
    if args.expected_size is not None and size != args.expected_size:
        raise SystemExit(f"Incomplete APK: expected {args.expected_size} bytes, received {size}")
    with args.apk.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    if args.expected_sha256 and digest != args.expected_sha256:
        raise SystemExit("APK SHA-256 differs from the verified signed build")
    try:
        with zipfile.ZipFile(args.apk) as archive:
            names = set(archive.namelist())
            if not {"AndroidManifest.xml", "classes.dex"} <= names:
                raise SystemExit("Not a complete standalone Android APK")
            # The APK is installed on ARM64 tablets and on other CPU
            # families. A missing native dependency previously surfaced only
            # after opening the OCR workflow, not at app startup. Reject it
            # *before delivery* for every supported ABI.
            native_required = (
                "libopencv_java4.so",
                "libonnxruntime.so",
                "libonnxruntime4j_jni.so",
                "libc++_shared.so",
            )
            supported_abis = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            for abi in supported_abis:
                for library in native_required:
                    entry_name = f"lib/{abi}/{library}"
                    if entry_name not in names:
                        raise SystemExit(
                            f"APK native library missing for {abi}: {library}"
                        )
                    entry = archive.getinfo(entry_name)
                    if entry.file_size < 10000:
                        raise SystemExit(
                            f"APK native library is too small for {abi}: {library}"
                        )
                    with archive.open(entry) as native_stream:
                        if native_stream.read(4) != b"\x7fELF":
                            raise SystemExit(
                                f"APK contains invalid ELF for {abi}: {library}"
                            )
            # A .so can be present yet fail at runtime because it targets
            # another CPU architecture or depends on a missing sibling .so.
            # Reject those errors before publishing any release.
            abi_architectures = {
                "arm64-v8a": (2, 183),
                "armeabi-v7a": (1, 40),
                "x86": (1, 3),
                "x86_64": (2, 62),
            }
            android_system_libs = {
                "libandroid.so", "libc.so", "libdl.so", "libm.so",
                "liblog.so", "libz.so", "libjnigraphics.so",
                "libmediandk.so", "libEGL.so", "libGLESv2.so",
                "libOpenSLES.so", "libvulkan.so", "libatomic.so",
                "libstdc++.so", "libcamera2ndk.so", "libaaudio.so",
                "libnativewindow.so", "libbinder_ndk.so",
                "libsync.so", "libunwind.so",
            }
            if not shutil.which("readelf"):
                raise SystemExit("Cannot verify native dependencies: readelf is missing")
            with tempfile.TemporaryDirectory(prefix="sorulab-elf-") as folder:
                for abi, (elf_class, machine) in abi_architectures.items():
                    entries = {
                        name.rsplit("/", 1)[-1]: name for name in names
                        if name.startswith(f"lib/{abi}/") and name.endswith(".so")
                    }
                    for lib_name, apk_entry in entries.items():
                        with archive.open(apk_entry) as native:
                            header = native.read(20)
                            if (len(header) < 20 or header[:4] != b"\x7fELF"
                                or header[5] != 1 or header[4] != elf_class
                                or struct.unpack("<H", header[18:20])[0] != machine):
                                raise SystemExit(
                                    f"APK library architecture incompatible: {abi}/{lib_name}"
                                )
                        local = Path(folder, abi, lib_name)
                        local.parent.mkdir(parents=True, exist_ok=True)
                        with archive.open(apk_entry) as native, local.open("wb") as out:
                            shutil.copyfileobj(native, out)
                        elf_info = subprocess.run(
                            ["readelf", "-d", str(local)],
                            capture_output=True, text=True
                        )
                        if elf_info.returncode:
                            raise SystemExit(f"Invalid native dynamic library: {abi}/{lib_name}")
                        needed = set(re.findall(
                            r"\(NEEDED\).*?\[([^]]+)\]", elf_info.stdout
                        ))
                        missing = needed.difference(entries).difference(android_system_libs)
                        if missing:
                            raise SystemExit(
                                f"APK library not found: {abi}/{lib_name} depends on "
                                f"{sorted(missing)}"
                            )
                    print(f"Native linkage verified on {abi}: {len(entries)} ELF libraries")
            bad_entry = archive.testzip()
            if bad_entry is not None:
                raise SystemExit(f"Corrupted APK entry: {bad_entry}")
    except zipfile.BadZipFile as error:
        raise SystemExit("Incomplete or invalid APK ZIP directory") from error
    print(f"APK integrity OK: {size} bytes; SHA-256 {digest}")


if __name__ == "__main__":
    main()
