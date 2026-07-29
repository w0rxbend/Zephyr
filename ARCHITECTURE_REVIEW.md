# Architecture Review

Reviewed: 2026-07-29

## Scope and outcome

Zephyr is a Kotlin Multiplatform Compose Desktop application that wraps the local SDKMAN installation and CLI. The current architecture keeps JVM filesystem, process, and environment access out of `commonMain`; this boundary is retained.

The implementation pass has focused on correctness and safe failure modes before presentation-only refactors.

## Completed improvements

- Hardened SDKMAN command execution with shell argument quoting, startup-file suppression, cancellation-driven process destruction, timeouts, bounded output capture, and control-sequence sanitization.
- Validated candidate and version identifiers before filesystem or command access.
- Ignore malformed or symlinked candidate/version entries during filesystem discovery and verify that `current` points to a direct installed version.
- Enforced the same identifier policy while parsing SDKMAN output, keeping malformed metadata out of the UI state.
- Force a stable POSIX locale for SDKMAN commands so parser behavior is independent of the desktop session locale.
- Made local-only scans and cleanup fail closed when remote metadata cannot be verified.
- Restricted cleanup to versions that are verified local-only and never the persisted default version.
- Injected the SDKMAN-home resolver so filesystem behavior is tested without touching a developer's real installation.
- Serialized state-changing SDKMAN operations, ignored repeated destructive requests, and queued only non-destructive detail loading.
- Replaced residual stale state snapshots with atomic updates and keep cached catalog installation markers synchronized after refreshes and mutations.
- Kept package-detail loading separate from mutation/refresh state, preventing stale detail requests from leaving the UI busy after navigation.
- Moved the bounded desktop-theme probe off the Compose UI thread and made IDE previews side-effect-free.
- Removed generated template code and placeholder tests.
- Extracted theme, application shell, JDK presentation, and shared UI primitives from the original monolithic composition root.
- Separated route-specific screens and version actions from the application composition root.
- Replaced placeholder candidate labels with project-owned Compose vector icons and removed unused icon-resource strings from the domain model.
- Added compiler warning-as-error gates and a least-privilege GitHub Actions verification workflow.
- Added scheduled CodeQL analysis for Kotlin/Java and pinned all workflow actions to immutable commits.
- Added Gradle SHA-256 dependency-verification metadata to protect artifact resolution.
- Added the MIT license and wired it into native distribution metadata.
- Removed unused lifecycle and test-engine declarations, eliminating an unnecessary beta dependency tree from the desktop runtime.
- Aligned native distribution targets with Linux and verified the portable application-image build.

## Verification coverage

- SDKMAN candidate and version parser behavior.
- Command input validation, cache invalidation, malformed filesystem/symlink scanning, local-only merge behavior, and cleanup safety.
- ViewModel error handling, operation deduplication, stale-route protection, queued detail loading, and navigation away from blocked detail requests.
- JDK search/grouping presentation logic.
- Headless Compose search-field interaction and state propagation.
- Command-output terminal-control sanitization.
- Hermetic Bash command-runner execution, fixed locale, and cancellation behavior; no test invokes a developer's real SDKMAN installation or network.
- Full Gradle `check` task plus AppImage, Debian, and RPM package generation, all enforced by CI under Temurin with the required Linux packagers installed.

## Remaining work

The following items need product or legal decisions, broader UI test infrastructure, or a separate feature scope:

- Broaden app-level Compose Desktop workflows and SDKMAN CLI fixture coverage as new features are added.
- Validate signed release artifacts on a controlled release runner before publishing.

## Architectural guardrails

- `commonMain` must not depend on JVM APIs.
- All SDKMAN commands must use the typed `SdkmanCommand` model and repository validation boundary.
- Destructive operations must verify safety conditions in the repository, not only in the UI.
- New coroutine operations must preserve cancellation and avoid stale state snapshots.
- New compiler warnings must be treated as build failures.
