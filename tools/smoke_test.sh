#!/usr/bin/env bash
#
# Drives a built CryptoUtils through a full backup/restore round trip by feeding its prompts on
# stdin, and checks the recovered file byte for byte.
#
# The JUnit suite only ever exercises classes on a JVM classpath. This is the one check that the
# thing actually shipped - native binary, self-extracting AWT libraries, reflection metadata, UPX
# compression and all - can still do its job.
#
# Usage: tools/smoke_test.sh <path-to-cryptoutils-binary>
#        tools/smoke_test.sh "java -jar build/libs/CryptoUtils-2.0.0.jar"

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "usage: $0 <cryptoutils command>" >&2
    exit 2
fi

CRYPTOUTILS="$1"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

cd "$WORKDIR"

printf 'A secret that must survive a round trip through paper.\n' > secret.txt

run_transcript() {
    # TERM=dumb so the prompts come out plain; the REPL reads answers line by line either way
    TERM=dumb $CRYPTOUTILS > transcript.log 2>&1
}

echo "== version =="
$CRYPTOUTILS --version

echo "== backup: 3 shares, 2 to recover, with container tags =="
run_transcript <<'EOF'
backup
file
smoke
Safe, shelf 2
split
3
2
secret.txt
restore
file
recovered.txt
shamir
3
smoke_1.png

smoke_3.png
exit
EOF

for expected in smoke_1.png smoke_2.png smoke_3.png \
                smoke_tag_1.png smoke_tag_2.png smoke_tag_3.png \
                smoke_print_1.png recovered.txt; do
    if [ ! -f "$expected" ]; then
        echo "FAIL: $expected was not produced" >&2
        echo "--- transcript ---" >&2
        cat transcript.log >&2
        exit 1
    fi
done

echo "== restore: shares 1 and 3, share 2 deliberately skipped =="
if ! cmp -s secret.txt recovered.txt; then
    echo "FAIL: recovered file differs from the original" >&2
    echo "--- transcript ---" >&2
    cat transcript.log >&2
    exit 1
fi

# every artifact holds secret material and must not be world-readable
for artifact in smoke_*.png recovered.txt; do
    perms="$(stat -c '%a' "$artifact")"

    if [ "$perms" != "600" ]; then
        echo "FAIL: $artifact has permissions $perms, expected 600" >&2
        exit 1
    fi
done

echo "== single card, no split =="
run_transcript <<'EOF'
backup
text
whole_card

single
one card holds all of it
restore
text
whole
whole_card_1.png
exit
EOF

if ! grep -q "one card holds all of it" transcript.log; then
    echo "FAIL: an unsplit backup did not come back off its card" >&2
    cat transcript.log >&2
    exit 1
fi

echo "== arguments are refused =="
if $CRYPTOUTILS backup file secret.txt > /dev/null 2>&1; then
    echo "FAIL: the binary accepted command-line arguments" >&2
    exit 1
fi

echo
echo "SMOKE TEST PASSED"
