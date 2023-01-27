# CryptoUtils

Terminal program for simple seed generation, encryption, decryption and more.

## Commands:

### Cryptocurrencies

```
seed [base64 string] - Generate or restore 12 and 24 seed phrase
seed_rsa_cipher <encrypt/decrypt> <base64 original/encrypted RSA entropy> <public/private RSA key path> - Encrypt/decrypt entropy by RSA
seed_ecdhe_cipher <encrypt/decrypt> <public ECDHE key path> <private ECDHE key path> <base64 original/encrypted ECDHE entropy> - Encrypt/decrypt entropy by ECDHE
```
### Cryptography
```
rsa_key <public RSA key file path> <private RSA key file path> [keys size] - Generate key pair for RSA
ecdhe_key <public ECDHE key file path> <private ECDHE key file path> - Generate key pair for ECDH + AES-GCM
shamir split <all parts> <parts for recover> <path> | join <result path> <part 1 | null> <part 2 | null> <part 3 | null> ... - Shamir's secret sharing algorithm
```

### Backups
```
qr scan <image path> [result path] | generate <result path> <pixels width> [error correction level] <source text/f:path> - Scan or generate image code
pdf417 scan <image path> [result path] | generate <result path> <pixels width> [error correction level] <source text/f:path> - Scan or generate image code
ecl - Show available error correction levels for qr command
hex <from/to> <source path> <result path> - Convert files to simple hex text-file```
```

## Common Commands Tips

### Seed Command

> seed [base64 entropy string]

Generate or restore 24 and 12 word mnemonic phrase (BIP39) from entropy.

Uses SecureRandom() 

### RSA Key Command
```
> rsa_key
```

Generate public and private RSA keys in base64 for cipher.

**Not recommended for use**

### RSA Cipher Command
```
> seed_rsa_cipher <encrypt/decrypt> <base64 public/private RSA key> <base64 original/encrypted RSA entropy>
```
Encrypt or decrypt entropy by RSA algorithm.

When decrypting, it automatically calls the seed command to output a mnemonic phrase

**Not recommended for use**

### ECDHE Key Command
```
> ecdhe_key
```
Generate public and private ECDH + AES-GCM keys in base64 for cipher.

### ECDHE Cipher Command
```
> seed_ecdhe_cipher <encrypt/decrypt> <base64 public ECDHE key> <base64 private ECDHE key> <base64 original/encrypted ECDHE entropy>
```
Encrypt or decrypt entropy by ECDH + AES-GCM algorithm.

> base64 public ECDHE key

The public key of the other side regardless of the mode (encrypt or decrypt)

> base64 private ECDHE key

My private key regardless of the mode (encrypt or decrypt)

When decrypting, it automatically calls the seed command to output a mnemonic phrase