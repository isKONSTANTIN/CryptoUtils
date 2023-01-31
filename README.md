# CryptoUtils

Terminal program for simple seed generation, encryption, decryption, backup and more.

## Commands

### Cryptocurrencies

```
seed [base64 string] - Generate or restore 12 and 24 seed phrase
seed_to_base <word_1> <word_2> ... - Transform seed words to base64 entropy without checksum
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
hex <encode/decode> <source path> <result path> - Convert files to simple hex text-file
```

### Misc
```
> cd <path> - Change current directory
```

In addition, some system commands can work without leaving the CU terminal. For example, md5sum, cat, gpg work fine if they are installed. But you can't call it graphics like nano.

## Common Commands Tips

### Seed Command
```
> seed [base64 entropy string]
```

Generate or restore 24 and 12 word mnemonic phrase (BIP39) from entropy.

Uses SecureRandom() 

### Seed To Base Command

``` 
> seed_to_base ...
```

Converts a mnemonic phrase into the original entropy and encodes it in Base64. Note that the command does not check the hash sum. Call the seed command with base64 result and check the last words

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

## Backup Commands Tips

### QR
```
> qr scan <image path> [result path] | generate <result path> <pixels width> [error correction level] <source text/f:path> - Scan or generate image code
```
Generates or scans a QR code. Please note that scanning requires a perfect picture. 

**Not recommended for use**

### HEX
```
> hex <encode/decode> <source path> <result path> - Convert files to simple hex text-file
```

Encodes any binary file into a HEX string. Ideal for printing keys or mnemonic phrase (entropy in base64/encrypted) in short form and decodes back

### Shamir's Secret Separation Scheme

``` 
> shamir split <all parts> <parts for recover> <path> | join <result path> <part 1 | null> <part 2 | null> <part 3 | null> ... - Shamir's secret sharing algorithm
```

It can split any binary file into N parts and any K of them allow restoring the original file. Note that the order of the parts is important for recovery. In addition, it is impossible to determine exactly whether the file was restored correctly.

## P.S.

THIS TOOL IS NOT PRODUCTION READY

Be sure to double-check the correctness of the recovery and the overall operation of the program. 