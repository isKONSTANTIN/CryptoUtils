# CryptoUtils
[![Github CI](https://github.com/isKONSTANTIN/CryptoUtils/actions/workflows/gradle.yml/badge.svg)](https://github.com/isKONSTANTIN/CryptoUtils/actions/workflows/gradle.yml)

Terminal program for simple seed generation, encryption, decryption, backup and more.

## What Is This Thing Good For?

Have you ever thought that writing a mnemonic phrase (or any other secret) on a piece of paper is not the safest storage? Physical damage, loss or declassification is not excluded.

It would be much better if we could safely divide into several parts and distribute them to our friends or relatives.
A simple separation will not work - if we lose at least one part, we would not be able to restore the data completely.

This tool allows you to [use the Shamir secret separation scheme](https://github.com/isKONSTANTIN/CryptoUtils/wiki/Commands#shamirs-secret-separation-scheme): divide into N parts, and any K will allow you to completely restore the original data. 

And there are also [commands](https://github.com/isKONSTANTIN/CryptoUtils/wiki/Commands#all-commands-list) for generating qr codes, converting binary files into hex-string for printing, and much more

Here is a photo of my containers with keys and a mnemonic phrase:

![keys](https://user-images.githubusercontent.com/20424507/216436036-83beaae5-8761-4c4c-b07f-bed3b9c6a229.png)

## Usage Example

<pre>cu&gt; gpg --output seed.gpg --recipient bob@example.com -e seed.txt

cu&gt; ls
CryptoUtils-0.2.1.jar
seed.gpg
seed.txt

cu&gt; shamir split 3 2 seed.gpg
Done!
cu&gt; hex encode seed.shp-1 seed.shp-1.hex
Done!
cu&gt; cat seed.shp-1.hex
CE40898B7ECB74EE31FAADD192064B1DD501D8CBC17A74341241A62D11CF4F6B75E6916CE281CE5911C2E5278A4C5CC
177DDB77F9931FB19FACA0F30FDA00E3090D84CBA3B4C6B1CE62915DC1F5D88B66C332B29CCAAB7BC3495C2EADA05D9

...

cu&gt; qr generate seed-2-qr.png 500 l f:seed.shp-2
Done
cu&gt; seed_to_base satoshi like gold
<span style="color:#06989A">go</span>at      <span style="color:#06989A">go</span>ddess   <span style="background-color:#FFFFFF"><span style="color:#1C1C1C">gold   </span></span>   <span style="color:#06989A">go</span>od      <span style="color:#06989A">go</span>ose     <span style="color:#06989A">go</span>rilla   <span style="color:#06989A">go</span>spel    <span style="color:#06989A">go</span>ssip    <span style="color:#06989A">go</span>vern    <span style="color:#06989A">go</span>wn</pre>

## Native Binary

CI builds a native executable (via [GraalVM native-image](https://www.graalvm.org/latest/reference-manual/native-image/)) for Linux, x86_64 and arm64 — no JVM installation required to run it. Download it from the "CryptoUtils-native-\*" artifacts on the [latest GitHub Actions run](https://github.com/isKONSTANTIN/CryptoUtils/actions/workflows/gradle.yml). On Windows, use the jar instead (`java -jar CryptoUtils-*.jar`, requires JVM 17+).

To build it yourself, the easiest way is via Docker, which reproduces the same build CI uses:

<pre>docker buildx build -f docker/native-build.Dockerfile --target export --output type=local,dest=out .</pre>

The binary is written to `out/cryptoutils`.

Alternatively, without Docker, install a [GraalVM](https://www.graalvm.org/downloads/) JDK (25 or newer) and run:

<pre>./gradlew nativeCompile</pre>

The binary is written to `build/native/nativeCompile/`.

## Wiki

Instructions for building, a list of all commands and the use of some can be found in the [Wiki](https://github.com/isKONSTANTIN/CryptoUtils/wiki/Using)

## This Tool Is Not Production Ready

Be sure to double-check the correctness of the recovery and the overall operation of the program. 