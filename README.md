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

### Sidebar navigation

- **Installed JDK** — locally installed Java versions.
- **Installed SDKs** — all other locally installed SDKMAN candidates.
- **Browse JDKs** — SDKMAN's remote Java catalog.
- **Browse SDKs** — all other remote SDKMAN candidates.
- **Local-Only Versions** — appears after a scan finds orphaned versions.

### Installed JDK screen

Displays installed Java versions as cards. Supports:

- Search by identifier, feature version, or provider name.
- Grouping by **feature version** (JDK 25, JDK 21, …) or by **provider** (Eclipse Temurin, Azul Zulu, Amazon Corretto, …).
- Current and default markers per version card.
- Local-only marker with a **Clean** button for orphaned versions.
- **Clean** is blocked for the currently active version — switch away first.

### Installed SDKs screen

Displays installed non-Java candidates as package cards. Each card shows the current version, installed version count, and a local-only marker when orphaned versions are present.

### Browse JDKs / Browse SDKs screens

Shows the full SDKMAN remote catalog with display names, descriptions, stable versions, and install status. Includes a search bar. Clicking a card opens the package detail page.

### Package detail pages

- Version list merged from the local filesystem and `sdk list <candidate>` remote output.
- Each version row shows installed, current, available, and local-only status.
- Actions per version: **Install**, **Use for This Session**, **Set as Default**, **Uninstall**.
- Local-only versions show a **Clean** button (blocked when the version is currently active).
- JDK detail supports the same grouping and search as the Installed JDK screen.

### Local-only cleanup

- Run **Scan** from the header bar to audit every installed candidate against SDKMAN's remote registry.
- The **Local-Only Versions** screen shows all affected packages as cards with orphaned version counts.
- **Clean** removes only the flagged versions after a confirmation dialog that lists what will be removed.
- If every installed version of a package is orphaned, the confirmation warns that the package may disappear from Installed after cleaning.
- Partial failures are reported; cleaning continues for remaining eligible versions.

### SDKMAN maintenance

- **Refresh** — reloads the local filesystem state.
- **Metadata** — runs `sdk update` to refresh the remote candidate catalog. Runs automatically before the first Browse load.
- **Check Updates** — runs `sdk selfupdate` after a confirmation dialog. SDKMAN may update itself if a new version is available.

### Header bar

Always shows:

- Current JDK identifier and provider (e.g. `Current JDK: 21.0.5-tem`).
- Default JDK when it differs from the current one.
- SDKMAN CLI version and installation path.

---

## SDKMAN Integration

Zephyr reads installed candidates directly from the filesystem:

```text
~/.sdkman/candidates/<candidate>/<version>/   ← installed version
~/.sdkman/candidates/<candidate>/current      ← symlink to active version
```

Remote versions come from `sdk list <candidate>`. The two sets are merged by version identifier to produce the local-only status.

All CLI commands run through `zsh -c` after sourcing `sdkman-init.sh`:

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

---

## Architecture

Kotlin Multiplatform + Compose Multiplatform desktop app.

| Module | Content |
| --- | --- |
| `shared/commonMain` | Domain models, repository interface, ViewModel, all Compose UI |
| `shared/jvmMain` | SDKMAN filesystem integration (Okio), CLI execution (Apache Commons Exec) |
| `shared/jvmTest` | Integration and parser tests |
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

The app is functional end-to-end. Work remaining:

- Test coverage — parser, scanner, and ViewModel tests are placeholder.
- Real candidate icons — currently using placeholder label boxes.
- Default JDK detection beyond the `current` symlink.
- Native distribution packaging.
