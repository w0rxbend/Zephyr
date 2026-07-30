# Zephyr Product and UX Roadmap

Reviewed: 2026-07-29

## Product position

Zephyr is the desktop control center for a developer's SDKMAN installation. It should make the local toolchain understandable at a glance, keep destructive actions safe, and reduce command-line archaeology without hiding the SDKMAN model.

The UI direction is a compact JetBrains-inspired workbench:

- Dense but calm information hierarchy.
- Flat toolbars and panels rather than oversized mobile surfaces.
- Restrained blue accent, clear focus states, and high-contrast status colors.
- Persistent navigation with separate workspace and application sections.
- Contextual actions near the information they affect.
- A bottom status bar for environment state and background work.
- Reusable widgets with one spacing, shape, typography, and interaction vocabulary.

## Current implementation slice

The first product slice delivers:

1. Overview dashboard with installation health, package counts, local-only risk, and quick actions.
2. Grouped workbench navigation.
3. Persistent System/Light/Dark theme preference.
4. Persistent Compact/Comfortable UI density.
5. Privacy setting for displaying the SDKMAN path in the application chrome.
6. SDKMAN diagnostics page.
7. About page with product, runtime, license, and project information.
8. Compact command toolbar and application status bar.
9. Shared panels, navigation rows, toolbar actions, metric tiles, settings rows, and segmented controls.
10. Search, result counts, filtering, and guided empty states across Installed, Browse, and Detail pages.
11. Explicit confirmation and shared destructive styling for uninstall and local-only cleanup.
12. Practical desktop window sizing and density-aware page/card layouts.
13. Typed transaction previews for every SDKMAN mutation, with validated command fields and affected versions shown before execution.
14. Searchable session operation history with timestamps, outcomes, structured command details, and CSV export.
15. Transaction-specific recovery guidance with safe refresh, rescan, diagnostics, and reviewed-retry actions.
16. Filesystem-backed disk-impact summaries in transaction previews, including exact reclaimable bytes and evidence-labeled install estimates.
17. Persistent protected-version pins with page-level controls and repository-enforced cleanup/uninstall blocking.
18. SDKMAN service reachability state with explicit network requirements and offline preflight behavior.
19. Independent SDKMAN integrity checks for scripts, directories, malformed entries, and broken or escaping default links.
20. Redacted support-bundle export with platform/runtime details, SDKMAN health, integrity results, inventory counts, and session operations.
21. Keyboard-first global search across workspace destinations, candidates, installed versions, settings, and maintenance actions.
22. Searchable command palette with direct keyboard shortcuts for frequent navigation and safe maintenance actions.
23. Persistent SDK and JDK-vendor favorites, prioritized in Browse and available as shortcuts from Overview.
24. Persistent, bounded recent-candidate history with Overview shortcuts back to the last viewed details.
25. Consolidated Update Center with stable-target detection, JDK/SDK grouping, selection, inspection, and reviewed installs.
26. Typed batch installs with combined disk estimates, sequential execution, and persistent per-item progress/results.
27. Typed batch uninstall with default/protected exclusions, exact reclaim estimates, and sequential per-item results.
28. Persistent named toolchain profiles captured from defaults, compared with local installs, and applicable through reviewed batches.
29. Native `.sdkmanrc` import with strict parsing, ignored-line warnings, and a read-only local-state diff.
30. Selectable `.sdkmanrc` export with deterministic output, native destination selection, and explicit overwrite confirmation.
31. Multi-version candidate comparison with explicit vendor, install, default, availability, local-only, and protection columns.
32. Shared native clipboard actions for candidate keys, versions, diagnostics, and validated equivalent SDKMAN commands.
33. Documented keyboard navigation with global workspace shortcuts, deterministic resolution tests, and visible custom-control focus.
34. Drag-resizable navigation with density-aware defaults, safe width bounds, persistence, and a Settings reset.
35. Independent persistent Cards/Table preferences for installed SDK and catalog browsing page families.
36. Composable JDK search, lifecycle, vendor, and sort controls with visible active filters and deterministic reset.
37. Persistent named JDK filter presets with one-click apply, same-name replacement, and explicit removal.

## Delivery log

| Slice | Status | Evidence |
| --- | --- | --- |
| Foundation | Delivered | Modern workbench, persistent settings, diagnostics, shared widget library, architecture hardening, and packaging gate. |
| SAFE-01 Transaction preview | Delivered | Install, default, uninstall, cleanup, metadata, and self-update actions require confirmation of a typed command plan. |
| SAFE-02 Operation journal | Delivered | Confirmed operations record running/success/failure state, remain searchable in-session, and export to redacted CSV. |
| SAFE-03 Rollback guidance | Delivered | Failed journal entries explain verified next steps and expose safe recovery actions; cleanup retries include only re-verified versions. |
| SAFE-04 Disk-impact estimate | Delivered | Previews show exact removable bytes, median-based install estimates, confidence, evidence, and current free space. |
| SAFE-05 Protected versions | Delivered | Pins persist locally, appear across version/cleanup views, and are enforced inside repository cleanup and uninstall boundaries. |
| SAFE-06 Offline awareness | Delivered | Toolbar, status bar, Overview, and Diagnostics show reachability; network operations preflight while local mutations remain available offline. |
| SAFE-07 SDKMAN integrity check | Delivered | Diagnostics independently reports required scripts, candidate storage, malformed entries, version entries, and default-link safety. |
| SAFE-08 Exportable diagnostics | Delivered | Diagnostics writes a collision-safe support report while redacting user-home and custom SDKMAN paths by default. |
| FLOW-01 Global search | Delivered | Ctrl/Cmd+K opens a ranked search that navigates or invokes typed maintenance actions entirely from the keyboard. |
| FLOW-02 Command palette | Delivered | Ctrl/Cmd+Shift+P opens command-only search; documented shortcuts invoke refresh, scan, and Diagnostics directly. |
| FLOW-03 Favorites | Delivered | SDK and JDK-vendor pins persist locally, sort first in Browse, and render as direct Overview destinations. |
| FLOW-04 Recent items | Delivered | Candidate detail navigation records a deduplicated six-item MRU list and exposes it on Overview across sessions. |
| FLOW-05 Update center | Delivered | Installed candidates with uninstalled stable targets are grouped, selectable, inspectable, and routed through typed install review. |
| FLOW-06 Batch install | Delivered | Update Center selections produce one typed plan, run sequentially, continue after item failures, and report every outcome. |
| FLOW-07 Batch uninstall | Delivered | Default and protected versions are excluded before selection; reviewed removals run sequentially with repository re-enforcement. |
| FLOW-08 Toolchain profiles | Delivered | Named default-version sets persist, report installed/missing targets, and install only missing entries through batch review. |
| FLOW-09 Project toolchain import | Delivered | Native file selection parses candidate/version entries and classifies current, default-change, and install requirements without mutation. |
| FLOW-10 Project toolchain export | Delivered | Selected defaults render deterministically and native saving never replaces an existing file without explicit confirmation. |
| FLOW-11 Candidate comparison | Delivered | Two or more loaded versions render in a compact table with vendor and explicit lifecycle/safety status. |
| FLOW-12 Copy actions | Delivered | Technical identifiers, visible diagnostics, and typed equivalent commands expose reusable native clipboard actions with outcome feedback. |
| UX-01 Keyboard navigation | Delivered | Global route/action shortcuts, Tab focus guidance, resolver tests, and visible sidebar/toolbar focus support keyboard-only use. |
| UX-02 Resizable navigation | Delivered | Sidebar dragging persists within tested 190–360 dp bounds and can return to the density-aware default from Settings. |
| UX-03 Table/card view choice | Delivered | Installed SDK and Browse SDK families independently persist visual-card or dense-table rendering without losing actions. |
| UX-04 Sort and filters | Delivered | JDK browsing composes query, installed/available/local-only state, vendor, and sort controls with visible clearable state. |
| UX-05 Saved filters | Delivered | Named JDK query/status/vendor/sort presets persist locally, apply atomically, replace by name, and can be removed. |
| UX-06 Accessible status language | Delivered | Status markers use distinct symbols and semantic labels; emphasized badges pair their text and color with tone-specific symbols. |
| UX-07 Scalable typography | Delivered | A persisted 100–200% text preference scales the complete type system and fixed-height chrome together. |
| UX-08 Reduced motion | Delivered | System, full, and reduced preferences control every indeterminate progress treatment; reduced mode uses a static labeled marker. |
| UX-09 Empty-state guidance | Delivered | Every full-page empty state explains the condition and offers a relevant refresh, reset, browse, choose, save, or select action. |
| UX-10 Context menus | Delivered | Right-click menus on cards and rows mirror safe inspect, copy, favorite, protect, install, default, uninstall, update, and clean actions. |
| AUTO-01 Scheduled metadata refresh | Delivered | Persisted Off/Hourly/6-hour/Daily schedules refresh only while the app is open, online, and idle, with cadence visible in status. |
| AUTO-02 Update notifications | Delivered | Persisted Off/Updates-only/All-checks policies produce deduplicated, path-free desktop notices from any loaded catalog refresh. |
| AUTO-03 Cleanup policy | Delivered | Opt-in 7/30/90-day first-seen tracking flags overdue local-only versions while preserving manual review and repository safety checks. |
| AUTO-04 CLI launch integration | Delivered | Installed versions can open in a supported terminal with `sdk use` scoped to that child shell and values passed outside shell text. |
| AUTO-05 Environment snapshot | Delivered | A first-class workspace captures, compares, parses, and exports installed versions and defaults in a deterministic versioned format. |
| AUTO-06 Snapshot restore plan | Delivered | Imported snapshots become typed install/default previews, run sequentially with per-step results, and recalculate unfinished work for safe resume. |
| AUTO-07 Operation notifications | Delivered | Configurable path-free desktop notices report long-running or all reviewed operation completions once per journal entry. |
| AUTO-08 Retry queue | Delivered | Typed installed/catalog/detail/integrity reads retry twice with visible bounded backoff, while every mutation remains single-attempt and user-controlled. |
| ECO-01 Candidate metadata cache | Delivered | Versioned catalog metadata persists in the platform cache, hydrates Browse offline, refreshes online, and exposes cached/live state plus age. |
| ECO-02 Vendor knowledge | Delivered | Versioned JDK vendor summaries expose maintainer and support characteristics with an authoritative HTTPS source per SDKMAN code. |
| ECO-03 Release notes links | Delivered | Update Center resolves known candidates to validated upstream HTTPS release histories through a browser boundary that rejects unsafe URLs. |
| ECO-04 Proxy configuration | Delivered | Explicit proxy coordinates apply only to SDKMAN children; passwords use Linux Secret Service and are rejected when secure storage is unavailable. |
| ECO-05 Custom SDKMAN home | Delivered | Native directory selection persists only normalized paths containing the required init script and candidates directory, with a clear automatic-discovery reset. |
| ECO-06 Portable preferences | Delivered | A deterministic native import/export format serializes an explicit non-sensitive allowlist while preserving and excluding all machine-local state. |
| ECO-07 Plugin-ready actions | Delivered | Versioned stable action IDs dispatch through a validated handler boundary without exposing ViewModel, Compose state, repositories, or confirmation bypasses. |
| ECO-08 Accessibility audit | Delivered | Automated role/selection/status/motion/scale and WCAG AA contrast gates pass alongside the checked-in keyboard and screen-reader audit checklist. |

## Groomed feature backlog

### P0 — safety and trust

| ID | Feature | User outcome | Acceptance signal |
| --- | --- | --- | --- |
| SAFE-01 | Transaction preview | See exact SDKMAN commands and affected versions before execution. | Confirmation shows typed command plan without shell text. |
| SAFE-02 | Operation journal | Review successful and failed mutations with timestamps. | Session history is searchable and exportable. |
| SAFE-03 | Rollback guidance | Recover quickly after a failed install/default change. | Failure surface proposes verified next steps. |
| SAFE-04 | Disk-impact estimate | Know expected reclaimed/required disk space. | Cleanup and install confirmations show estimates. |
| SAFE-05 | Protected versions | Pin versions that must never be cleaned. | Repository safety boundary enforces pins. |
| SAFE-06 | Offline awareness | Understand which actions need the network. | Offline state is visible before an operation. |
| SAFE-07 | SDKMAN integrity check | Detect missing scripts, broken symlinks, and invalid candidate entries. | Diagnostics reports each check independently. |
| SAFE-08 | Exportable diagnostics | Share a redacted support bundle safely. | Export excludes home paths by default. |

### P1 — daily workflow

| ID | Feature | User outcome | Acceptance signal |
| --- | --- | --- | --- |
| FLOW-01 | Global search | Find candidates, versions, settings, and actions from one field. | Keyboard-first search opens any destination. |
| FLOW-02 | Command palette | Run common actions without navigating. | Searchable action list supports shortcuts. |
| FLOW-03 | Favorites | Pin frequently managed SDKs and JDK vendors. | Favorites appear on Overview and Browse. |
| FLOW-04 | Recent items | Return to recently viewed candidates. | Last-viewed list persists locally. |
| FLOW-05 | Update center | See all available candidate updates together. | Update candidates are grouped and selectable. |
| FLOW-06 | Batch install | Install a curated toolchain in one workflow. | Operations run sequentially with per-item status. |
| FLOW-07 | Batch uninstall | Remove selected non-default versions safely. | Default and pinned versions remain protected. |
| FLOW-08 | Toolchain profiles | Save named sets such as “Backend” or “Android”. | Profiles can be compared with local state. |
| FLOW-09 | Project toolchain import | Read `.sdkmanrc` and show required changes. | Import presents a reviewable diff. |
| FLOW-10 | Project toolchain export | Generate `.sdkmanrc` from selected defaults. | Export never overwrites without confirmation. |
| FLOW-11 | Candidate comparison | Compare version, vendor, status, and install state. | Two or more versions render in a compact table. |
| FLOW-12 | Copy actions | Copy identifiers, paths, and equivalent SDKMAN commands. | Every technical value has a discoverable copy action. |

### P1 — navigation and presentation

| ID | Feature | User outcome | Acceptance signal |
| --- | --- | --- | --- |
| UX-01 | Keyboard navigation | Use the entire application without a mouse. | Focus order and shortcuts are documented and tested. |
| UX-02 | Resizable navigation | Allocate space according to display size. | Sidebar width persists within safe bounds. |
| UX-03 | Table/card view choice | Switch between visual browsing and dense scanning. | Preference persists per page family. |
| UX-04 | Sort and filters | Filter by installed, available, vendor, status, and version. | Active filters are visible and clearable. |
| UX-05 | Saved filters | Reuse common catalog queries. | Named filters persist locally. |
| UX-06 | Accessible status language | Never rely on color alone. | Badges pair icon/color with explicit labels. |
| UX-07 | Scalable typography | Respect desktop text scaling. | Layout remains usable at 125–200%. |
| UX-08 | Reduced motion | Disable nonessential animations. | System and explicit preferences are honored. |
| UX-09 | Empty-state guidance | Every empty page explains the next useful action. | Empty states include relevant primary actions. |
| UX-10 | Context menus | Expose copy, install, default, uninstall, and inspect actions. | Right-click mirrors visible safe actions. |

### P2 — automation and observability

| ID | Feature | User outcome | Acceptance signal |
| --- | --- | --- | --- |
| AUTO-01 | Scheduled metadata refresh | Keep catalog information fresh automatically. | Schedule is opt-in and visible in status. |
| AUTO-02 | Update notifications | Learn about toolchain updates without opening Browse. | Notification policy is configurable. |
| AUTO-03 | Cleanup policy | Flag local-only versions after a grace period. | No automatic deletion; review remains mandatory. |
| AUTO-04 | CLI launch integration | Open a terminal with the selected SDK activated. | Activation is scoped to the launched terminal. |
| AUTO-05 | Environment snapshot | Capture current defaults and installed versions. | Snapshot diff is stable and exportable. |
| AUTO-06 | Snapshot restore plan | Recreate a toolchain from a snapshot. | Restore is previewable and resumable. |
| AUTO-07 | Operation notifications | Surface completion of long-running installs. | Desktop notifications contain no sensitive paths. |
| AUTO-08 | Retry queue | Retry transient network failures safely. | Only idempotent/read operations auto-retry. |

### P2 — ecosystem and release quality

| ID | Feature | User outcome | Acceptance signal |
| --- | --- | --- | --- |
| ECO-01 | Candidate metadata cache | Browse recent catalog data offline. | Cache has age and refresh indicators. |
| ECO-02 | Vendor knowledge | Explain JDK distributions and support characteristics. | Curated data is versioned and sourced. |
| ECO-03 | Release notes links | Open upstream changes before updating. | Links are validated HTTPS URLs. |
| ECO-04 | Proxy configuration | Work in managed enterprise networks. | Proxy credentials use platform-safe storage. |
| ECO-05 | Custom SDKMAN home | Manage a non-default installation explicitly. | Path selection is validated before saving. |
| ECO-06 | Portable preferences | Export/import non-sensitive UI settings. | Secrets and machine paths are excluded. |
| ECO-07 | Plugin-ready actions | Add integrations without coupling them to core state. | Stable internal action contract is documented. |
| ECO-08 | Accessibility audit | Maintain keyboard, contrast, and screen-reader quality. | Automated semantics tests and manual checklist pass. |

## Design guardrails

- Never hide a destructive side effect behind an icon-only control.
- Never expose raw terminal control sequences or unvalidated remote links.
- Keep default-version and cleanup protection in the repository, not only in the UI.
- Prefer progressive disclosure: common actions stay visible; advanced actions live in menus or Diagnostics.
- A setting must affect real behavior before it is exposed.
- Do not add dashboard charts when a number and clear status communicate better.
- New widgets belong in the shared component layer and must support light and dark themes.
- Every new page requires an empty, loading, success, and failure-state review.
