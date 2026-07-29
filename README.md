# Zephyr

A desktop GUI for [SDKMAN](https://sdkman.io/) built with Kotlin Multiplatform and Compose Desktop.
Zephyr wraps the SDKMAN CLI and local filesystem — it does not reimplement package management.

---

## The Idea

SDKMAN is excellent for installing and switching JDKs and SDKs from the terminal. But over time it accumulates **local-only versions**: builds that are installed in `~/.sdkman/candidates/` but are no longer listed in SDKMAN's remote registry.

This happens when:

- A distribution vendor pulls a specific build from the catalog.
- A version identifier changes upstream (a patch release gets replaced).
- A provider is removed or renamed in SDKMAN.

These orphaned versions waste disk space, cannot be updated through `sdk upgrade`, and are only visible if you run `sdk list <candidate>` and scan the output for the `local only` marker — one candidate at a time.

**Zephyr's primary goal is to surface local-only versions across all installed candidates in one place and make cleanup safe and straightforward.**

---

## Terminology

Zephyr uses user-facing labels instead of SDKMAN's internal "candidate" term:

- The `java` candidate is always presented as **JDK**.
- All other candidates are presented as **SDKs**.
- Internal code keeps using `Candidate` because it maps directly to SDKMAN.
- Java version identifiers are shown as JDK versions: `JDK 25`, `JDK 21`, `JDK 17`.

---

## Current Functionality

### Workbench and navigation

- **Overview** — default JDK, installed-version totals, local-only risk, health checks, and quick actions.
- **Installed JDK** — locally installed Java versions.
- **Installed SDKs** — all other locally installed SDKMAN candidates.
- **Browse JDKs** — SDKMAN's remote Java catalog.
- **Browse SDKs** — all other remote SDKMAN candidates.
- **Local-Only Versions** — orphan review and an explicit rescan workflow.
- **Diagnostics** — read-only SDKMAN installation, metadata, and updater health.
- **Operation History** — searchable session journal with timestamps, command fields, outcomes, and CSV export.
- **Settings** — persisted theme, information density, and SDKMAN-path privacy controls.
- **About** — application version, runtime integration, project links, and license.

Navigation is grouped into Workspace, Discover, Maintenance, and application sections. A compact toolbar exposes global maintenance actions, while the bottom status bar reports background work, candidate count, default JDK, and SDKMAN state.

### Installed JDK screen

Displays installed Java versions as cards. Supports:

- Search by identifier, feature version, or provider name.
- Grouping by **feature version** (JDK 25, JDK 21, …) or by **provider** (Eclipse Temurin, Azul Zulu, Amazon Corretto, …).
- Default marker per version card, derived from SDKMAN's persisted `current` symlink.
- Local-only marker with a **Clean** button for orphaned versions.
- **Clean** is blocked for the default version — choose another default first.

### Installed SDKs screen

Displays installed non-Java candidates as package cards. Each card shows the default version, installed version count, and a local-only marker when orphaned versions are present. Search covers display names, SDKMAN keys, and default versions.

### Browse JDKs / Browse SDKs screens

Shows the full SDKMAN remote catalog with display names, descriptions, stable versions, and install status. Search, Installed/Available filters, live result counts, adaptive grids, and guided empty states make large catalogs easier to scan. Clicking a card opens the package detail page.

### Package detail pages

- Version list merged from the local filesystem and `sdk list <candidate>` remote output.
- Each version row shows installed, default, available, and local-only status.
- Actions per version: **Install**, **Set as Default**, **Uninstall**.
- Non-default installed versions expose **Make default** directly.
- Uninstall always requires an explicit destructive-action confirmation.
- Local-only versions show a **Clean** button (blocked when the version is the default).
- JDK detail supports the same grouping and search as the Installed JDK screen.

### Local-only cleanup

- Run **Scan** from the header bar to audit every installed candidate against SDKMAN's remote registry.
- The **Local-Only Versions** screen shows all affected packages as cards with orphaned version counts.
- **Clean** removes only the flagged versions after a typed transaction preview lists every affected version.
- If every installed version of a package is orphaned, the confirmation warns that the package may disappear from Installed after cleaning.
- Partial failures are reported; cleaning continues for remaining eligible versions.

### Transaction previews

Every SDKMAN mutation is reviewed before execution:

- Install, make-default, uninstall, cleanup, metadata refresh, and SDKMAN self-update actions create a typed command plan.
- The confirmation shows the exact action, candidate, and version fields without constructing or exposing shell expressions.
- Uninstall and cleanup previews calculate exact reclaimable bytes from local version directories.
- Install previews estimate required space from the median size of installed sibling versions and clearly label the confidence and evidence.
- Current free space appears beside the estimate when the platform can measure it.
- Candidate and version identifiers are validated both when the plan is created and again at the repository boundary.
- Destructive transactions use distinct warning styling and remain cancellable.
- Confirmed transactions are recorded from start through success or failure in the session operation journal.

### Operation journal

- Search by operation, candidate, version, status, or outcome.
- Running, successful, and failed operations use explicit status language.
- Failed entries provide transaction-specific recovery steps and safe actions for refresh, rescan, diagnostics, or reviewed retry.
- Cleanup retries retain only versions that are still verified as local-only after a new scan.
- Export writes a collision-safe CSV to `~/Downloads` when available, falling back to the user home directory.
- Export content contains operation data but excludes the SDKMAN home path.

### Protected versions

- Pin any installed version from Installed JDK or a package detail page.
- Protected badges remain visible in version lists and local-only cleanup cards.
- Candidate-level cleanup automatically excludes pinned versions and explains when every local-only version is protected.
- Protection is persisted for the desktop user.
- Cleanup and uninstall enforce protection again inside the repository, so bypassing the UI cannot remove a pinned version.

### SDKMAN maintenance

- **Refresh** — reloads the local filesystem state.
- **Metadata** — runs `sdk update` to refresh the remote candidate catalog. Runs automatically before the first Browse load.
- **Check Updates** — runs `sdk selfupdate` after a confirmation dialog. SDKMAN may update itself if a new version is available.

### Appearance and desktop behavior

- JetBrains-inspired light and dark palettes with a restrained blue accent.
- Persistent **System**, **Light**, and **Dark** theme modes.
- Persistent **Compact** and **Comfortable** information density.
- Optional hiding of the machine-specific SDKMAN home path.
- A 1280×820 default window with a practical minimum size for the workbench layout.
- Shared panels, navigation items, segmented controls, metric tiles, settings rows, status indicators, toolbar controls, and destructive buttons across pages.

---

## SDKMAN Integration

Zephyr reads installed candidates directly from the filesystem:

```text
~/.sdkman/candidates/<candidate>/<version>/   ← installed version
~/.sdkman/candidates/<candidate>/current      ← symlink to persisted default version
```

Remote versions come from `sdk list <candidate>`. The two sets are merged by version identifier to produce the local-only status.

All CLI commands run through a non-interactive `/bin/bash` process after sourcing `sdkman-init.sh`:

```bash
source "$SDKMAN_DIR/bin/sdkman-init.sh" && sdk list java
```

The UI never constructs shell strings directly. All commands go through a typed `SdkmanCommand` sealed interface implemented in `jvmMain`.

---

## Screenshots

![Installed JDK screen](Screenshot-1.png)

![Installed SDKs screen](Screenshot-2.png)

![Browse SDKs screen](Screenshot-3.png)

---

## Running

Requires SDKMAN to be installed. If SDKMAN is not detected the app shows an error page with install instructions and a **Retry** button.

```bash
# Hot reload (recommended during development)
./gradlew :desktopApp:hotRun --auto

# Standard run
./gradlew :desktopApp:run
```

### Tests

```bash
./gradlew :shared:jvmTest
```

Gradle verifies dependency checksums using [`gradle/verification-metadata.xml`](gradle/verification-metadata.xml). Regenerate it only as part of a reviewed dependency update:

```bash
./gradlew --write-verification-metadata sha256 check :desktopApp:packageAppImage
```

### Linux packages

```bash
# Portable application image
./gradlew :desktopApp:packageAppImage

# Native package formats (require a JDK jpackage installation with the matching system packager)
./gradlew :desktopApp:packageDeb
./gradlew :desktopApp:packageRpm
```

---

## Architecture

Kotlin Multiplatform + Compose Multiplatform desktop app.

The current technical review and completed hardening work are tracked in [ARCHITECTURE_REVIEW.md](ARCHITECTURE_REVIEW.md). The groomed product backlog, UX direction, and design guardrails are in [PRODUCT_UX_ROADMAP.md](PRODUCT_UX_ROADMAP.md).

| Module | Content |
| --- | --- |
| `shared/commonMain` | Domain models, repository interface, ViewModel, all Compose UI |
| `shared/jvmMain` | SDKMAN filesystem integration (Okio), CLI execution (Apache Commons Exec) |
| `shared/commonTest`, `shared/jvmTest` | UI interaction, integration, and parser tests |
| `desktopApp` | JVM entry point and window configuration |

Source set boundaries are strict: `commonMain` contains no JVM-only APIs. Process execution, filesystem access, and OS environment reads live entirely in `jvmMain`.

### Key dependencies

| Library | Use |
| --- | --- |
| Compose Multiplatform | UI framework |
| Okio | `~/.sdkman` filesystem operations; `FakeFileSystem` in tests |
| Apache Commons Exec | SDKMAN CLI process execution with timeouts and stream capture |
| kotlinx.coroutines | Async state management, background dispatcher for IO |
| androidx.lifecycle | `ViewModel` and `StateFlow`-based UI state |

---

## Status

The app is functional end-to-end. Parser, repository safety/cache, ViewModel failure paths, settings persistence, and headless Compose interactions are covered. The prioritized roadmap captures broader workflow, accessibility, automation, and ecosystem opportunities.

## License

Zephyr is licensed under the [MIT License](LICENSE).
