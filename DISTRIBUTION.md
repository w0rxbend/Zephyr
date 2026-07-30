# Linux distribution

Zephyr `1.0.0` is packaged natively on GitHub-hosted `amd64` and `arm64`
machines. Native builds matter because Compose Desktop bundles an
architecture-specific JVM and Skiko library; the build does not cross-compile
those components.

## Release outputs

| Format | Architectures | Publication |
| --- | --- | --- |
| AppImage | `amd64`, `arm64` | GitHub Releases |
| Snap | `amd64`, `arm64` | GitHub Releases and Snap Store `stable` |
| Flatpak bundle | `amd64`, `arm64` | GitHub Releases |

The tag is the release trigger. Every package/architecture build is isolated
and allowed to fail independently. The final job downloads all successful
artifacts and publishes the GitHub release if at least one `.AppImage`,
`.flatpak`, or `.snap` exists. It fails only when none of the distributions
could be produced. `SHA256SUMS` and `BUILD-MANIFEST.txt` describe the exact
assets that survived the build.

## Local builds

Build the current machine's real single-file AppImage:

```shell
packaging/appimage/build-appimage.sh
```

Build the current machine's Flatpak bundle (the script installs the
Freedesktop runtime and Flatpak Builder for the current user):

```shell
packaging/flatpak/build-flatpak.sh
```

The Snap is built from `snap/snapcraft.yaml` with Snapcraft:

```shell
snapcraft
```

Generated release files are written to `dist/`.

For a version bump, update `zephyrVersion` in `gradle.properties`, the Snap
`version` in `snap/snapcraft.yaml`, and the AppStream release in
`packaging/linux/com.worxbend.zephyr.metainfo.xml` together.

## AppImage design

Compose's `createDistributable`/`packageAppImage` output is a self-contained
application directory, despite the task name; it is not a single-file
AppImage. `packaging/appimage/build-appimage.sh` converts that directory to a
conforming AppDir with `AppRun`, desktop metadata, icon, and AppStream
metadata, then packages it using AppImage `appimagetool` 1.9.1. Both tool
binaries, the type-2 runtimes, and their SHA-256 digests are pinned.

## Snap Store prerequisites

Zephyr requires classic confinement because its purpose is to operate on the
host's SDKMAN installation and host toolchains. Canonical manually reviews
classic snaps. Follow [`snap/STORE_REVIEW.md`](snap/STORE_REVIEW.md) to
register the name, request approval, and configure the scoped
`SNAPCRAFT_STORE_CREDENTIALS` Actions secret. Builds and GitHub publication do
not depend on the secret; only the store-upload step does.

## Flatpak scope and Flathub preparation

The release bundle wraps the same Compose application image used by the other
formats in the Freedesktop 25.08 runtime. It requests home access because
SDKMAN intentionally lives under the user's home directory, plus network,
display, notification, Secret Service, and host-spawn access.

`packaging/flatpak/com.worxbend.zephyr.yml` is directly buildable for release
bundles. A future Flathub submission should instead compile from source inside
`flatpak-builder`. Since network access is disabled during module builds,
generate and commit the complete Gradle dependency source list with the
official `flatpak-builder-tools` Gradle generator, pin the source commit, and
submit the manifest to Flathub. The current prebuilt-image source is
deliberately not represented as Flathub-ready.

## Primary references

- [GitHub-hosted runner architectures](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
- [Compose native distributions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)
- [AppImage manual packaging](https://docs.appimage.org/packaging-guide/manual.html)
- [Snapcraft platforms and architectures](https://snapcraft.io/docs/reference/project-file/snapcraft-yaml)
- [Snap classic-confinement review](https://snapcraft.io/docs/reference/administration/reviewing-classic-confinement-snaps/)
- [Snapcraft publish action](https://github.com/snapcore/action-publish)
- [Flatpak manifests](https://docs.flatpak.org/en/latest/manifests.html)
- [Flatpak Gradle dependency generator](https://github.com/flatpak/flatpak-builder-tools/tree/master/gradle)
- [Freedesktop Platform 25.08](https://flathub.org/apps/org.freedesktop.Platform)
