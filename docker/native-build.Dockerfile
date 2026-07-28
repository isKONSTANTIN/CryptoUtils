# Builds the CryptoUtils native binary reproducibly, without relying on
# GitHub Actions-specific setup steps. Runs natively per host architecture
# (no QEMU) - build with --platform matching the target (linux/amd64 or
# linux/arm64) and buildx picks the right base image layer automatically.
#
# Usage:
#   docker buildx build -f docker/native-build.Dockerfile \
#       --target export --output type=local,dest=out .
# Produces out/cryptoutils
#
# To also produce .deb/.rpm packages, target package-export instead and
# pass VERSION and PKG_ARCH (deb arch: amd64/arm64):
#   docker buildx build -f docker/native-build.Dockerfile \
#       --target package-export \
#       --build-arg VERSION=1.0.0 --build-arg PKG_ARCH=amd64 \
#       --output type=local,dest=out .
# Produces out/cryptoutils_<version>_<arch>.deb and out/cryptoutils-<version>-1.<rpmarch>.rpm

FROM ghcr.io/graalvm/graalvm-community:25 AS graalvm

# The Gradle daemon can't run on JDK 25 (native-image's JDK), so the build
# stage uses JDK 17 to drive Gradle, and points it at the GraalVM install
# (copied in below) purely as the toolchain used for the nativeCompile task.
FROM eclipse-temurin:17-jdk AS build

COPY --from=graalvm /opt/graalvm-community-java25 /opt/graalvm-community-java25

RUN apt-get update && apt-get install -y --no-install-recommends zlib1g-dev build-essential \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
COPY . .

RUN ./gradlew nativeCompile --no-daemon \
    -Porg.gradle.java.installations.paths=/opt/graalvm-community-java25

FROM scratch AS export
COPY --from=build /workspace/build/native/nativeCompile/cryptoutils /cryptoutils

# Wraps the native binary (already static/self-contained, so no bundled
# runtime is needed) into .deb/.rpm with fpm, which builds both formats from
# one set of metadata. rpm (the packaging tool) is installed even on this
# Debian-based image because fpm shells out to rpmbuild to produce .rpm.
FROM eclipse-temurin:17-jdk AS package

ARG VERSION
ARG PKG_ARCH

RUN apt-get update && apt-get install -y --no-install-recommends \
        ruby ruby-dev rpm build-essential \
    && rm -rf /var/lib/apt/lists/* \
    && gem install --no-document fpm

COPY --from=build /workspace/build/native/nativeCompile/cryptoutils /pkgroot/usr/bin/cryptoutils
RUN chmod 755 /pkgroot/usr/bin/cryptoutils

WORKDIR /out
RUN set -eu; \
    case "$PKG_ARCH" in \
        amd64) RPM_ARCH=x86_64 ;; \
        arm64) RPM_ARCH=aarch64 ;; \
        *) echo "Unknown PKG_ARCH: $PKG_ARCH" >&2; exit 1 ;; \
    esac; \
    COMMON_ARGS="-s dir -n cryptoutils -v $VERSION \
        --license GPL-3.0-only \
        --url https://github.com/isKONSTANTIN/CryptoUtils \
        --description \"Terminal program for simple seed generation, encryption, decryption, backup and more.\""; \
    eval fpm $COMMON_ARGS -t deb -a "$PKG_ARCH" --deb-no-default-config-files \
        -C /pkgroot usr/bin/cryptoutils; \
    eval fpm $COMMON_ARGS -t rpm -a "$RPM_ARCH" \
        -C /pkgroot usr/bin/cryptoutils

FROM scratch AS package-export
COPY --from=package /out/*.deb /out/*.rpm /
