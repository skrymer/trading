# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

This is a **single-context** repo: one `CONTEXT.md` + `docs/adr/` at the repo root.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root — the domain glossary (Edge, Win rate, Profit factor, forward return, lift, ARS, candidate, config hash, etc.).
- **`docs/adr/`** — read the ADRs that touch the area you're about to work in (ADR 0001 rich domain objects, 0003 provider abstractions, 0005 walk-forward aggregation, 0007 condition-screen leakage boundary, 0008 strategy-exploration brake, …).

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The producer skill (`/grill-with-docs`) creates them lazily when terms or decisions actually get resolved.

## File structure

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-rich-domain-objects.md
│   └── …
└── udgaard/ midgaard/ asgaard/
```

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids (e.g. use **Edge**, not "expectancy" / "expected value").

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/grill-with-docs`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0001 (rich domain objects) — but worth reopening because…_
