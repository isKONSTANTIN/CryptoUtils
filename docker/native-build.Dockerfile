# Builds the CryptoUtils native binary reproducibly, without relying on
# GitHub Actions-specific setup steps. Runs natively per host architecture
# (no QEMU) - build with --platform matching the target (linux/amd64 or
# linux/arm64) and buildx picks the right base image layer automatically.
#
# Usage:
#   docker buildx build -f docker/native-build.Dockerfile \
#       --target export --output type=local,dest=out .
# Produces out/cryptoutils

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
