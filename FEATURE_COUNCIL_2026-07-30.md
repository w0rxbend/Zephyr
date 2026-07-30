# Zephyr Feature Council — 2026-07-30

## Process

Ten independent idea generators inspected the current repository from product strategy, daily SDKMAN use,
information architecture, accessibility, safety/reliability, automation, enterprise/offline operation,
Kotlin architecture, performance/observability, and ecosystem-integration perspectives.

The 20 raw proposals were deduplicated into eight finalists. Three blind judges scored every finalist for
user value, novelty, feasibility, and safety. A five-member quorum then approved a maximum of four proposals;
selection required at least three approvals.

## Capability assessment

- Typed reviews already protect install, uninstall, cleanup, default, metadata, self-update, profile, and
  snapshot mutations.
- Sequential batch execution, per-step journaling, resumable task history, and mutation postcondition checks
  provide a strong recovery foundation.
- Repository boundaries validate identifiers and protect current defaults, pinned versions, and local-only
  cleanup without fresh remote evidence.
- Desired-state monitoring identifies missing versions, default drift, extra installations, and desired
  local-only versions, while automated repair remains limited to installs and defaults.
- Storage Center measures payloads safely but does not yet solve target-based reclamation.
- Profiles, project workspaces, snapshots, and desired baselines are capable but remain separate requirement
  sources.
- Proxy credentials already reach SDKMAN children safely, but the old connectivity check used a separate
  direct route.
- Local-only reads were previously sequential and withheld all results until the slowest candidate finished.

## Finalists and vote

| ID | Proposal | Effort | Risk | Approvals |
| --- | --- | --- | --- | --- |
| F1 | Reviewed desired-state convergence | Large | Medium | 0/5 |
| F2 | Goal-based storage reclamation planner | Large | Medium | 0/5 |
| F3 | Install and activate stable updates | Medium | Low | **4/5 — selected** |
| F4 | Reference-aware removal guard | Medium | Medium | 0/5 |
| F5 | Bound transaction review with state-drift interlock | Medium | Low | 0/5 |
| F6 | Proxy-routed SDKMAN connection diagnostics | Medium | Medium | **5/5 — selected** |
| F7 | Progressive bounded-concurrency local-only audit | Medium | Medium | **4/5 — selected** |
| F8 | Toolchain demand matrix and unified install plan | Medium | Low | 1/5 |

## Delivered selected slate

### F3 — Install and activate stable updates

- Stable targets are classified as missing, installed-but-inactive, or active.
- Update Center keeps missing and inactive targets visible and reviews install-before-default plans.
- A failed install skips only its dependent default change; unrelated work continues with durable per-step
  results.
- Switch-only activation works offline, while plans containing installs use the route-aware preflight.
- Transaction persistence, recovery, progress, notifications, UI language, and tests cover the new plan.

### F6 — Proxy-routed SDKMAN connection diagnostics

- Direct and proxy routes produce bounded, safe online/proxy-auth/TLS/timeout/service/indeterminate results.
- The read-only probe uses the sanitized SDKMAN proxy environment, disables curl configuration files, keeps
  credentials out of argv, and never silently falls back to a direct route.
- Transaction preflight, Diagnostics, global actions, and support export consume classification-only data.
- Cancellation, overlapping checks, watchdog timeouts, route normalization, redaction, and zero-mutation
  behavior are regression-tested.

### F7 — Progressive bounded-concurrency local-only audit

- A configurable bounded worker pool scans an immutable installed-candidate snapshot.
- Deterministic partial results, active candidates, completed/total progress, and isolated failures publish
  incrementally to accessible Local-Only and status surfaces.
- Failed reads can be retried without repeating successful reads.
- Cleanup is gated to completed live evidence and still receives typed review plus fresh repository
  verification.
- Transaction-review admission, cancellation, zero mutation, stale-snapshot invalidation, concurrency bounds,
  partial publication, failure isolation, and retry scope are regression-tested.

## Verification

- `./gradlew :shared:jvmTest --no-daemon` — passed, 185 tests.
- `./gradlew build --no-daemon` — passed for shared and desktop modules.
- `git diff --check` — passed.
- A four-lane adversarial acceptance audit found eleven issues; every confirmed issue was remediated.
- A final independent remediation verifier returned no findings with high confidence.
