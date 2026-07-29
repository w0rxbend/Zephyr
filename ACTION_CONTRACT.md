# Zephyr internal action contract

Contract version: `1`

Integrations invoke capabilities with `ZephyrActionRequest`; they do not receive `ZephyrViewModel`, Compose state, repositories, or filesystem services. Unknown IDs, versions, parameters, control characters, and oversized values are rejected before dispatch.

Stable IDs:

- `zephyr.read.refresh-installed`
- `zephyr.read.scan-local-only`
- `zephyr.read.refresh-connectivity`
- `zephyr.review.refresh-metadata`
- `zephyr.review.check-sdkman-updates`

`read` actions run immediately. `review` actions only open Zephyr's existing typed transaction preview; an integration cannot bypass confirmation. Contract additions must preserve existing IDs and semantics within version 1. Breaking changes require a new contract version.
