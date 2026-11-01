#!/bin/sh
# SPDX-FileCopyrightText: 2026 The Elkslop contributors
# SPDX-License-Identifier: BSD-3-Clause
#
# Download the ROM source reconstructions described in README.md into this
# folder, at the versions we used, and check each file's SHA-256.
#
#   docs/rom-sources/fetch.sh
#
# Needs curl, tar and unzip. If 64tass is installed, it also generates the
# OS 1.00 listing (electron-os-1.00/elk100.lst) and checks it rebuilds the ROM.
set -eu

here="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d "${TMPDIR:-/tmp}/rom-sources.XXXXXX")"
trap 'rm -rf "$work"' EXIT

OS_COMMIT=f07f7f9f284e61ad75264faef0aef5ebf1f52952
OS_URL="https://codeload.github.com/tom-seddon/electron_os_disassembly/tar.gz/$OS_COMMIT"
BASIC_URL="https://mdfs.net/Software/BBCBasic/BBC/Basic2.zip"
OS_ROM_SHA256=efc902c006192c29198ada6125995e65eca29800486833b926c6eb9fb7578350

if command -v sha256sum >/dev/null 2>&1; then
    sha256() { sha256sum "$1" | cut -d' ' -f1; }
else
    sha256() { shasum -a 256 "$1" | cut -d' ' -f1; }
fi

failures=0
# check FILE EXPECTED_SHA256 [warn]: report a mismatch; "warn" means don't fail.
check() {
    actual="$(sha256 "$1")"
    if [ "$actual" = "$2" ]; then
        echo "  ok    ${1#"$here"/}"
    elif [ "${3:-}" = warn ]; then
        echo "  NEWER ${1#"$here"/} (upstream has changed since we pinned it)"
    else
        echo "  FAIL  ${1#"$here"/}: SHA-256 $actual, expected $2" >&2
        failures=$((failures + 1))
    fi
}

echo "Electron OS 1.00 disassembly (tom-seddon/electron_os_disassembly @ ${OS_COMMIT%"${OS_COMMIT#???????}"})"
curl -fsSL "$OS_URL" -o "$work/os.tar.gz"
mkdir "$work/os"
tar -xzf "$work/os.tar.gz" -C "$work/os" --strip-components=1
os="$here/electron-os-1.00"
mkdir -p "$os/src"
cp "$work/os/elk100.s65" "$os/"
cp "$work/os/src/"*.s65 "$os/src/"
cp "$work/os/README.md" "$os/UPSTREAM-README.md"
check "$os/elk100.s65"                      2724b89226039d928120a32b99f97805711521d68c24def693903141117b7211
check "$os/src/LDE13.s65"                   9fc736ac7404d01a5c89e082379487f5d36b6ed2001ed47609c76c43603f1ba3
check "$os/src/electron_os.s65"             a2b5d495a6caa7fdd1b78fb292daecc1268f86609ad8c04ad39dfeb41cb8ce27
check "$os/src/oscli.s65"                   c7807b44b20034ff98cb73b04561c89f3576b2e5d16bc20bf7cd7a6e6a2fecd4
check "$os/src/readDigitFromString.s65"     5c3a7e27036b30ccad969ccf0ad71d62c3b8e2eaed23edbdc9d04e1ce1f16d02
check "$os/src/readHexDigitFromString.s65"  7de7805ebe685b2b06e477bcbe87b2f5ddf7c83d726a78ef4951e9d068488fe2
check "$os/src/readLetterFromString.s65"    ec839c7ef886c4fb90bb89e7386d153646a3a72525e761b251000663d5dc2789

if command -v 64tass >/dev/null 2>&1; then
    (cd "$work/os" && 64tass --m6502 --nostart -Wall -q --case-sensitive --line-numbers \
        --verbose-list elk100.s65 -L elk100.lst -o elk100.rom)
    if [ "$(sha256 "$work/os/elk100.rom")" = "$OS_ROM_SHA256" ]; then
        cp "$work/os/elk100.lst" "$os/"
        echo "  ok    electron-os-1.00/elk100.lst (rebuilt ROM matches the original)"
    else
        echo "  FAIL  rebuilt ROM does not match the original; listing not copied" >&2
        failures=$((failures + 1))
    fi
else
    echo "  skip  electron-os-1.00/elk100.lst: install 64tass to generate the listing"
fi

echo "BBC BASIC II source reconstruction (mdfs.net)"
curl -fsSL "$BASIC_URL" -o "$work/Basic2.zip"
mkdir -p "$here/basic-2"
unzip -o -q -j "$work/Basic2.zip" Basic2.src -d "$here/basic-2"
# J.G. Harston updates this archive from time to time, so a newer file is
# expected eventually; it's reported, not treated as an error.
check "$here/basic-2/Basic2.src" 4a12ab096520ac934e586f79dc0f696dbec62aa45ada8e88057533a2607f4eb6 warn

if [ "$failures" -ne 0 ]; then
    echo "$failures file(s) failed verification." >&2
    exit 1
fi
echo "Done."
