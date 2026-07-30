# Contributing

Thanks for considering a contribution to CryptoUtils.

## Reporting bugs / requesting features

Open a [GitHub Issue](https://github.com/isKONSTANTIN/CryptoUtils/issues/new/choose) using the appropriate template. For anything involving a potential security issue (e.g. weak randomness, a broken cipher, a way to leak a secret), see [SECURITY.md](SECURITY.md) instead — please don't open a public issue for those.

## Building the project

The build runs on JDK 17. Gradle 8.14 supports JDK 24 and below, so if your default JDK is newer, point the daemon at an older one in a git-ignored `gradle.properties`:

```
org.gradle.java.home=/path/to/jdk-17
```

```
./gradlew build   # compile, test and check coverage; produces build/libs/CryptoUtils-*.jar
./gradlew test    # run tests only
./gradlew check   # tests plus the coverage gate
```

A native binary build additionally requires a [GraalVM](https://www.graalvm.org/downloads/) JDK (25 or newer):

```
./gradlew nativeCompile   # produces build/native/nativeCompile/cryptoutils
```

or reproducibly via Docker — see the [Installation](README.md#native-binary) section of the README.

## Making changes

1. Fork the repo and create a branch off `main`.
2. Keep the change focused — unrelated formatting/refactoring in the same PR makes it harder to review.
3. Add or update tests for any behavior change (`src/test/java`). Anything in `su.knst.crypto.core` is covered to a threshold enforced by `./gradlew check`, so new logic there needs tests to land.
4. Run `./gradlew build` locally before opening the PR — CI runs the same `test`/`build`/`build-native` jobs and will block on failures.
5. Open a PR against `main` describing what changed and why.

## Where code goes

Three layers, and the boundary between them is the point:

- **`core`** — every decision the tool makes: splitting, compression, rendering policy, reassembly. It knows nothing about terminals, prompts or output formatting, which is why it can be tested directly. `core` may use `utils`, never `cli`.
- **`cli`** — collecting answers from the user (`Ask`, `Questioner`) and formatting results. No logic lives here.
- **`utils`** — leaf helpers underneath both.

New behaviour belongs in `core`, with tests against it. If a command grows a `try` block that decides something, that decision probably wants to be a `core` method instead.

## Adding a new command

Commands live under `src/main/java/su/knst/crypto/command/commands/`, each extending `Command` and registered by name in `Main`'s constructor. A command is a prompt sequence: implement `run(Ask)` to gather answers, call into `core`, and turn the result into a `CommandResult` — see `BackupCreateCommand` for the shape. `description()` and `tag()` place it in `help`.

Commands that should also accept their argument on the same line (as `cd` and `help` do) extend `LineCommand` instead. Everything else prompts, and text typed after the command name is reported back as ignored.

If you add a command, please also add its row to the relevant table in the README.
