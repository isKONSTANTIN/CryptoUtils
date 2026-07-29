# Contributing

Thanks for considering a contribution to CryptoUtils.

## Reporting bugs / requesting features

Open a [GitHub Issue](https://github.com/isKONSTANTIN/CryptoUtils/issues/new/choose) using the appropriate template. For anything involving a potential security issue (e.g. weak randomness, a broken cipher, a way to leak a secret), see [SECURITY.md](SECURITY.md) instead — please don't open a public issue for those.

## Building the project

Requires JDK 17+.

```
./gradlew build   # compile + run tests, produces build/libs/CryptoUtils-*.jar
./gradlew test    # run tests only
```

A native binary build additionally requires a [GraalVM](https://www.graalvm.org/downloads/) JDK (25 or newer):

```
./gradlew nativeCompile   # produces build/native/nativeCompile/cryptoutils
```

or reproducibly via Docker — see the [Installation](README.md#native-binary) section of the README.

## Making changes

1. Fork the repo and create a branch off `main`.
2. Keep the change focused — unrelated formatting/refactoring in the same PR makes it harder to review.
3. Add or update tests for any behavior change (`src/test/java`).
4. Run `./gradlew build` locally before opening the PR — CI runs the same `test`/`build`/`build-native` jobs and will block on failures.
5. Open a PR against `main` describing what changed and why.

## Adding a new command

Commands live under `src/main/java/su/knst/crypto/command/commands/`, each extending the `Command` base class and registered by name in `Main`'s constructor. Look at an existing command in the same tag group (`BACKUPS`, `CRYPTOGRAPHY`, `CRYPTOCURRENCIES`, `MISC`) for the expected shape — `run()`/`description()`/`args()`/`tag()`, and support for both scripted (`ScriptedArgSource`) and interactive (`InteractiveArgSource`) invocation. If you add a command, please also add its row to the relevant table in the README.
