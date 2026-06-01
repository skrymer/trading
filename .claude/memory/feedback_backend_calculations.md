---
name: Keep calculations in backend
description: Stats and business logic calculations should be in the backend (Kotlin), not frontend, so they can be unit tested
type: feedback
originSessionId: c2c817e6-8f7f-4cfb-9e7c-c27ec54cc8f2
---
Keep calculations in the backend, not the frontend.

**Why:** Backend calculations can be unit tested. Frontend computed properties cannot be tested as easily and risk diverging from backend logic.

**How to apply:** When adding new stats, metrics, or business logic, create a backend endpoint/service that computes the values and returns them via API. The frontend should just display the results.
