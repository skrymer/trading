---
name: Pin provider config in compose files
description: When changing the default ingest provider for an environment, edit the compose.yaml directly rather than passing APP_INGEST_PROVIDER via the deploy shell.
type: feedback
originSessionId: e9db63a6-5658-4517-8fe1-254dedcdebcf
---
When switching `APP_INGEST_PROVIDER` for dev or prod, change the default in the relevant compose file (`udgaard/compose.yaml` for dev, `compose.prod.yaml` for prod), e.g. `${APP_INGEST_PROVIDER:-eodhd}`.

Why: Pinning in the compose file is durable and reviewable in git — env vars passed at deploy time evaporate after the shell session ends and aren't visible in the codebase. Prod was already pinned this way (see commit `Pin prod midgaard to EODHD provider`).

How to apply: For provider toggles or any other env-driven config that should persist across deploys, change the default in the compose file. Reserve shell-env overrides for one-off experiments.
