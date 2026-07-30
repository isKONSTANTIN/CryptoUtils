#!/usr/bin/env bash
#
# Drives a built CryptoUtils through every command and every mode it has, by feeding its prompts on
# stdin and checking what comes back out.
#
# The JUnit suite only ever exercises classes on a JVM classpath. This is the one check that the
# thing actually shipped - native binary, self-extracting AWT libraries, reflection metadata, UPX
# compression and all - can still do its job.
#
# Usage: tools/smoke_test.sh <path-to-cryptoutils-binary>
#        tools/smoke_test.sh "java -jar build/libs/CryptoUtils-2.0.0.jar"

set -uo pipefail

if [ $# -lt 1 ]; then
    echo "usage: $0 <cryptoutils command>" >&2
    exit 2
fi

CRYPTOUTILS="$1"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

cd "$WORKDIR"

FAILURES=0
CURRENT="(none)"

# ---- harness ---------------------------------------------------------------------------------

section() {
    CURRENT="$1"
    printf '\n== %s ==\n' "$1"
}

fail() {
    echo "  FAIL [$CURRENT]: $1" >&2

    if [ -f transcript.log ]; then
        echo "  --- transcript ---" >&2
        sed 's/^/  | /' transcript.log >&2
    fi

    FAILURES=$((FAILURES + 1))
}

pass() {
    echo "  ok: $1"
}

# Feeds the here-doc on stdin to the REPL. TERM=dumb keeps the prompts plain; answers are read
# line by line either way.
run() {
    TERM=dumb $CRYPTOUTILS > transcript.log 2>&1
}

expect_output() {
    if grep -qF "$1" transcript.log; then
        pass "output contains '$1'"
    else
        fail "expected output to contain '$1'"
    fi
}

expect_no_output() {
    if grep -qF "$1" transcript.log; then
        fail "expected output NOT to contain '$1'"
    else
        pass "output does not contain '$1'"
    fi
}

expect_file() {
    if [ -f "$1" ]; then
        pass "$1 exists"
    else
        fail "$1 was not produced"
    fi
}

expect_no_file() {
    if [ -f "$1" ]; then
        fail "$1 should not exist"
    else
        pass "$1 does not exist"
    fi
}

expect_same() {
    if cmp -s "$1" "$2"; then
        pass "$2 matches $1"
    else
        fail "$2 differs from $1"
    fi
}

expect_owner_only() {
    local perms
    perms="$(stat -c '%a' "$1" 2>/dev/null)"

    if [ "$perms" = "600" ]; then
        pass "$1 is owner-only"
    else
        fail "$1 has permissions ${perms:-missing}, expected 600"
    fi
}

# ---- fixtures --------------------------------------------------------------------------------

printf 'A secret that must survive a round trip through paper.\n' > secret.txt
head -c 6000 /dev/urandom > big.bin
mkdir -p subdir

SEED_PHRASE="legal winner thank year wave sausage worth useful legal winner thank yellow"
SEED_HEX="7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F7F"
SEED_BASE64="f39/f39/f39/f39/f39/fw=="

# ---- version and argument handling -------------------------------------------------------------

section "version"
if [ "$($CRYPTOUTILS --version)" = "$(grep -oP "(?<=^version ')[^']+" "$REPO/build.gradle")" ]; then
    pass "--version matches build.gradle"
else
    fail "--version does not match build.gradle"
fi

if [ "$($CRYPTOUTILS -v)" = "$($CRYPTOUTILS --version)" ]; then
    pass "-v is the same as --version"
else
    fail "-v disagrees with --version"
fi

section "command-line arguments are refused"
if $CRYPTOUTILS backup file secret.txt > /dev/null 2>&1; then
    fail "the binary accepted command-line arguments"
else
    pass "arguments exit non-zero"
fi

# ---- help, cd, shell fallthrough ---------------------------------------------------------------

section "help"
run <<'EOF'
help
exit
EOF
expect_output "backup"
expect_output "restore"
expect_output "seed"
expect_output "delete"
expect_output "cd"
expect_output "help"
expect_output "exit"
# removed in v2 - if any of these come back, the registry regressed
expect_no_output "shamir "
expect_no_output "rsa_key"
expect_no_output "pdf417"

section "help <command> on the same line"
run <<'EOF'
help backup
exit
EOF
expect_output "backup"
expect_no_output "ignored:"

section "help for an unknown command"
run <<'EOF'
help definitely_not_a_command
exit
EOF
expect_output "Unknown command"

section "cd with the path on the line"
run <<'EOF'
cd subdir
exit
EOF
expect_output "subdir"

section "cd prompting for the path"
run <<'EOF'
cd
subdir
exit
EOF
expect_output "subdir"

section "cd to somewhere that is not there"
run <<'EOF'
cd no_such_directory_anywhere
exit
EOF
expect_output "secret.txt"

section "arguments after a command are reported, not swallowed"
run <<'EOF'
backup file secret.txt

exit
EOF
expect_output "ignored: file secret.txt"

section "an unknown command falls through to the shell"
run <<'EOF'
echo shell-fallthrough-works
exit
EOF
expect_output "shell-fallthrough-works"

# ---- seed --------------------------------------------------------------------------------------

section "seed generate"
run <<'EOF'
seed
generate
exit
EOF
expect_output "Source entropy"
expect_output "12-word seed"
expect_output "24-word seed"

section "seed from_hex"
run <<EOF
seed
from_hex
$SEED_HEX
exit
EOF
expect_output "12-word seed"

section "seed from_base"
run <<EOF
seed
from_base
$SEED_BASE64
exit
EOF
expect_output "12-word seed"

section "seed from_base with rubbish input"
run <<'EOF'
seed
from_base
not!valid!base64!
exit
EOF
expect_output "base64"

section "seed to_hex"
run <<EOF
seed
to_hex
$SEED_PHRASE
exit
EOF
expect_output "Hex encoded"

section "seed to_base"
run <<EOF
seed
to_base
$SEED_PHRASE
exit
EOF
expect_output "Base64 encoded"

section "seed to_hex rejects a phrase that is not in the wordlist"
run <<'EOF'
seed
to_hex
legal winner thank year wave sausage worth useful legal winner thank definitelynotaword
exit
EOF
expect_output "not found in"

section "seed extend"
run <<EOF
seed
extend
$SEED_PHRASE
exit
EOF
expect_output "Extended entropy"
expect_output "24-word seed"

section "seed extend refuses a 24-word phrase"
run <<'EOF'
seed
extend
legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth useful legal winner thank year wave sausage worth title
exit
EOF
expect_output "Wrong mnemonic size"

section "seed wordlist list"
run <<'EOF'
seed
wordlist
list
exit
EOF
expect_output "bip39_english"
expect_output "bip39_japanese"

section "seed wordlist set"
run <<'EOF'
seed
wordlist
set
bip39_french
seed
wordlist
set
bip39_english
exit
EOF
expect_output "bip39_french"
expect_output "bip39_english"

# ---- backup: file ------------------------------------------------------------------------------

section "backup file, Shamir split, with container tags"
run <<'EOF'
backup
file
demo_file
Safe, shelf 2
split
3
2
secret.txt
exit
EOF
expect_output "3 parts, 2 required to recover"
expect_output "Type: FILE"

for i in 1 2 3; do
    expect_file "demo_file_$i.png"
    expect_owner_only "demo_file_$i.png"
    expect_file "demo_file_tag_$i.png"
done

expect_file "demo_file_print_1.png"
expect_owner_only "demo_file_print_1.png"

section "restore file from shares 1 and 3, share 2 skipped"
run <<'EOF'
restore
file
recovered_file.txt
shamir
3
demo_file_1.png

demo_file_3.png
exit
EOF
expect_output "Restored file written to recovered_file.txt"
expect_same secret.txt recovered_file.txt
expect_owner_only recovered_file.txt

section "backup file on a single card, no tags"
run <<'EOF'
backup
file
single_file

single
secret.txt
exit
EOF
expect_output "a single card holding the whole secret"
expect_file "single_file_1.png"
expect_no_file "single_file_tag_1.png"
# one artifact is already its own sheet
expect_no_file "single_file_print_1.png"

section "restore that single card as a whole backup"
run <<'EOF'
restore
file
recovered_single.txt
whole
single_file_1.png
exit
EOF
expect_same secret.txt recovered_single.txt

# ---- backup: text ------------------------------------------------------------------------------

section "backup text, Shamir split"
run <<'EOF'
backup
text
demo_text

split
3
2
correct horse battery staple
exit
EOF
expect_output "Type: TEXT"
expect_file "demo_text_1.png"

section "restore text"
run <<'EOF'
restore
text
shamir
3
demo_text_1.png
demo_text_2.png

exit
EOF
expect_output "correct horse battery staple"

section "backup and restore text with non-ASCII characters"
run <<'EOF'
backup
text
demo_unicode

single
секрет: пароль от сейфа
restore
text
whole
demo_unicode_1.png
exit
EOF
expect_output "секрет: пароль от сейфа"

# ---- backup: seed ------------------------------------------------------------------------------

section "backup seed, Shamir split"
run <<EOF
backup
seed
demo_seed

split
3
2
$SEED_PHRASE
exit
EOF
expect_output "Type: SEED"
expect_file "demo_seed_1.png"

section "restore seed"
run <<'EOF'
restore
seed
shamir
3

demo_seed_2.png
demo_seed_3.png
exit
EOF
expect_output "Recovered seed"
expect_output "legal winner thank"

section "backup seed on a single card and restore it whole"
run <<EOF
backup
seed
seed_single

single
$SEED_PHRASE
restore
seed
whole
seed_single_1.png
exit
EOF
expect_output "legal winner thank"

section "backup seed refuses a phrase that is not in the wordlist"
run <<'EOF'
backup
seed
bad_seed

split
3
2
legal winner thank year wave sausage worth useful legal winner thank definitelynotaword
exit
EOF
expect_no_file "bad_seed_1.png"

# ---- backup: hex reprint -------------------------------------------------------------------------

section "restore refuses a chunk that is not hex"
run <<'EOF'
restore
text
shamir
2
not-a-hex-string-at-all

exit
EOF
expect_output "invalid hex"

section "restore with nothing supplied at all"
run <<'EOF'
restore
text
shamir
3



exit
EOF
expect_output "No chunks provided"

section "restore of a whole backup refuses several cards"
run <<'EOF'
restore
text
whole
demo_text_1.png
exit
EOF
expect_output "decompress"

# ---- backup: oversized payload -------------------------------------------------------------------

section "a payload too large for any QR still produces cards plus .hex files"
run <<'EOF'
backup
file
big
Vault
split
2
2
big.bin
exit
EOF
expect_output "QR code: none"
expect_file "big_1.png"
expect_file "big_1.hex"
expect_file "big_2.hex"
expect_owner_only "big_1.hex"

section "restore that oversized backup from its .hex files"
run <<'EOF'
restore
file
recovered_big.bin
shamir
2
big_1.hex
big_2.hex
exit
EOF
# a .hex file is a path, so it is read as a card image and rejected - hex is typed in, not loaded
expect_output "Chunk 1"

section "reprint a lost card from a share's known hex"
# big_1.hex holds share 1 of the backup above verbatim, which is exactly what a user would have
# written down for a card that later got damaged
BIG_1="$(cat big_1.hex)"
run <<EOF
backup
hex
big_reprint

2
2
2
$BIG_1
exit
EOF
expect_output "Card reprinted: share 2 of 2"
expect_file "big_reprint_2.png"

section "a reprint carries the original payload byte for byte"
expect_file "big_reprint_2.hex"
if [ -f big_reprint_2.hex ] && [ "$(cat big_reprint_2.hex)" = "$BIG_1" ]; then
    pass "the reprinted card's payload is unchanged"
else
    fail "the reprinted payload was re-encoded"
fi

section "a reprint refuses a share number outside its scheme"
run <<EOF
backup
hex
bad_reprint

5
3
2
$BIG_1
exit
EOF
expect_output "Share index must be between 1 and 3"
expect_no_file "bad_reprint_5.png"

section "a reprint refuses a payload that is not hex"
run <<'EOF'
backup
hex
not_hex_reprint

1
2
2
zzzz-not-hex
exit
EOF
expect_no_file "not_hex_reprint_1.png"

section "restore the oversized backup by pasting the hex"
BIG_2="$(cat big_2.hex)"
run <<EOF
restore
file
recovered_big.bin
shamir
2
$BIG_1
$BIG_2
exit
EOF
expect_same big.bin recovered_big.bin

# ---- invalid schemes -----------------------------------------------------------------------------

section "a threshold larger than the number of parts is refused"
run <<'EOF'
backup
text
bad_scheme

split
2
5
nope
exit
EOF
expect_output "All parts must be >= parts for recover"
expect_no_file "bad_scheme_1.png"

section "a threshold below two is refused"
run <<'EOF'
backup
text
bad_k

split
3
1
nope
exit
EOF
expect_output "Parts for recover must be >= 2"
expect_no_file "bad_k_1.png"

section "cancelling at the first prompt writes nothing"
run <<'EOF'
backup

exit
EOF
expect_output "No input"

# ---- delete ----------------------------------------------------------------------------------

section "delete overwrites and removes a file"
printf 'delete me\n' > doomed.txt
run <<'EOF'
delete
doomed.txt
y
exit
EOF
expect_output "File was deleted"
expect_no_file doomed.txt

section "delete can be declined"
printf 'keep me\n' > spared.txt
run <<'EOF'
delete
spared.txt
n
exit
EOF
expect_output "File was NOT deleted"
expect_file spared.txt

section "delete of a file that is not there"
run <<'EOF'
delete
no_such_file_anywhere

exit
EOF
expect_output "No input"

# ---- exit ------------------------------------------------------------------------------------

section "q is an alias for exit"
run <<'EOF'
q
EOF
if [ -s transcript.log ]; then
    pass "q exits cleanly"
else
    fail "q produced no output at all"
fi

# ---- result ----------------------------------------------------------------------------------

echo
if [ "$FAILURES" -eq 0 ]; then
    echo "SMOKE TEST PASSED"
    exit 0
fi

echo "SMOKE TEST FAILED: $FAILURES check(s)" >&2
exit 1
