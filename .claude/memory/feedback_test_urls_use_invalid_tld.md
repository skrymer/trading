---
name: feedback-test-urls-use-invalid-tld
description: "Test URLs (RestClient/HTTP stubs etc.) use an obviously-fake host like `.invalid` TLD, not real local ports"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 1b1635b1-fd6c-4e97-ae82-2644c1f0f37c
---

Test code that constructs URLs for HTTP mocking (e.g. `MockRestServiceServer`, WireMock stub) should use an obviously-non-real host. Prefer the RFC-reserved `.invalid` TLD (e.g. `http://midgaard-test.invalid`) over `http://localhost:8081`-style URLs that collide visually with real running services.

**Why:** If the mock-binding ever regresses (Spring builder wiring breaks, MockRestServiceServer doesn't intercept), a localhost URL silently hits whatever is actually listening on that port — could be the real Midgaard in dev, could be some other service entirely. Tests that "pass" against an unintended real service are the worst kind of false positive. A `.invalid` URL fails loudly with a DNS resolution error.

**How to apply:** When writing tests that program HTTP expectations via `mockServer.expect(requestTo(...))` or set `*.base-url` properties for tests, pick a hostname that is *obviously* non-resolvable: `*.invalid`, `*.test`, `nonexistent.example.com`. Reserve `localhost:<port>` for tests that genuinely need to bind a real local server (e.g. WireMock instances, `@SpringBootTest` random-port containers).
