#!/usr/bin/env bash
set -euo pipefail

project_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
version=${ZEPHYR_VERSION:-$(sed -n 's/^zephyrVersion=//p' "$project_root/gradle.properties")}

case "$(uname -m)" in
    x86_64)
        flatpak_arch=x86_64
        release_arch=amd64
        ;;
    aarch64|arm64)
        flatpak_arch=aarch64
        release_arch=arm64
        ;;
    *)
        echo "Unsupported Flatpak architecture: $(uname -m)" >&2
        exit 1
        ;;
esac

if [[ ${ZEPHYR_SKIP_GRADLE_BUILD:-0} != 1 ]]; then
    "$project_root/gradlew" -p "$project_root" \
        -PzephyrVersion="$version" \
        :desktopApp:createDistributable \
        --console=plain
fi

flatpak remote-add --user --if-not-exists flathub \
    https://dl.flathub.org/repo/flathub.flatpakrepo

build_root="$project_root/build/flatpak/$flatpak_arch"
build_dir="$build_root/build-dir"
repo_dir="$build_root/repo"
rm -rf "$build_dir" "$repo_dir"
mkdir -p "$build_root" "$project_root/dist"

flatpak-builder \
    --arch="$flatpak_arch" \
    --force-clean \
    --user \
    --install-deps-from=flathub \
    --repo="$repo_dir" \
    "$build_dir" \
    "$project_root/packaging/flatpak/com.worxbend.zephyr.yml"

output="$project_root/dist/Zephyr-$version-linux-$release_arch.flatpak"
flatpak build-bundle \
    --arch="$flatpak_arch" \
    "$repo_dir" \
    "$output" \
    com.worxbend.zephyr
echo "$output"
