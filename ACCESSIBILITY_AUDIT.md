# Accessibility audit

Audit date: 2026-07-30  
Scope: Compose Desktop workbench, shared controls, navigation, dialogs, status presentation, light/dark themes, 100–200% text scaling.

## Automated checks

- [x] Primary navigation, toolbar buttons, search results, accordion headers, nested actions, and segmented controls expose button/radio roles and click actions.
- [x] Selected navigation, search results, and segmented options expose selected state.
- [x] Progress has a reduced-motion semantic label; status tones expose text labels and distinct non-color symbols.
- [x] Core light/dark text pairs meet WCAG AA 4.5:1 contrast. The test covers primary, primary-container, surface, and surface-variant pairs.
- [x] Typography scales through 200% and fixed-height chrome scales with it.
- [x] Empty states retain an interactive next action and context actions are keyboard-invokable menu items.

Executable coverage: `ZephyrUiPrimitivesTest`, `KeyboardShortcutsTest`, and `GlobalSearchTest`.

## Manual checklist

- [x] Keyboard: Tab/Shift+Tab reaches sidebar, toolbar, page controls, settings fields, and dialog actions with a visible focus border.
- [x] Keyboard: Ctrl/Cmd+K and Ctrl/Cmd+Shift+P open search; arrow keys move results; Enter activates; Escape closes.
- [x] Keyboard: documented route/action shortcuts resolve without colliding and review-class actions still open confirmation.
- [x] Screen reader: decorative candidate icons are hidden; interactive controls retain visible text names.
- [x] Screen reader: selection, status, error, progress, installed/default/protected, and destructive-review state are conveyed in text or semantics rather than color alone.
- [x] Zoom: 100%, 150%, and 200% settings remain scrollable; no workflow depends on clipped off-screen controls.
- [x] Motion: reduced mode replaces indeterminate animation with a static labeled indicator.
- [x] Contrast: light and dark palettes pass the automated core-pair gate; focus is shown with both border position and color.

## Release rule

Any new shared interactive primitive must expose a role, accessible name, enabled/selected state where applicable, visible keyboard focus, and a Compose semantics test. New palette pairs used for text must be added to the contrast test. This checklist is rerun before changing the release version.
