# CryptoUtils

Terminal program for simple seed generation, encryption and decryption

## Commands:

```
help - Show list of commands
exit - Exit from CryptoUtils
seed [base64 string] - Generate or restore 12 and 24 seed phrase
rsa_key - Generate key pair for RSA
ecdhe_key - Generate key pair for ECDH + AES-GCM
seed_rsa_cipher <encrypt/decrypt> <base64 public/private RSA key> <base64 original/encrypted RSA entropy> - Encrypt/decrypt entropy by RSA
seed_ecdhe_cipher <encrypt/decrypt> <base64 public ECDHE key> <base64 private ECDHE key> <base64 original/encrypted ECDHE entropy> - Encrypt/decrypt entropy by ECDHE
```

### Seed Command
```
> seed [base64 entropy string]
```

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