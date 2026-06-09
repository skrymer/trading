---
type: source
title: PEAD premise-class adjudication — RECONSIDER_CLASS (quant consult, 2026-06-09)
summary: Quant RECONSIDER_CLASS — abandon PEAD's long-only-drift form; EPS-sign gate is a stronger control than a sector residual, so #143 is predicted-dead for PEAD; next class = structurally beta-hedged.
status: stable
tags: [candidate, event-driven, earnings, quant-consult, methodology]
sources: ["strategy_exploration/dossier/condition-earningsepsgatedresidual.jsonl", "knowledge/wiki/sources/2026-06-09-pead-eps-gated-residual-screen-reject.md"]
related: ["[[pead]]", "[[beta-delivery]]", "[[2026-06-09-pead-eps-gated-residual-screen-reject]]", "[[2026-06-09-pead-market-neutral-residual-screen-reject]]", "[[purpose]]"]
updated: 2026-06-09
---

# PEAD premise-class adjudication — RECONSIDER_CLASS

After the [[2026-06-09-pead-eps-gated-residual-screen-reject|EPS-gated residual]] exhausted PEAD's
surprise-proxy axis, the pre-registered fork ("reconsider premise class" vs "deferred sector-neutral
residual / issue #143") was routed to the `quant-analyst`. **Verdict: RECONSIDER_CLASS** — abandon
PEAD's current long-only-drift form; do **not** spend #143 on PEAD.

## The diagnostic crux — the three deaths are ONE failure under strengthening controls

The rejects are not three independent failures; they are the same failure re-confirmed under
progressively stronger neutralisation, and *what each control removed* predicts the sector residual's
outcome:

| Proxy | Beta source it stripped | Flat-tape 20d | What it proved |
|---|---|---:|---|
| Raw gap | nothing | −0.38% | gap reads market direction |
| Market-neutral residual | same-night **SPY-index** gap | −0.52% / −0.20% | beta irreducible to the contemporaneous index move |
| EPS-sign-gated residual | the **fundamental surprise sign** itself | −0.31% | beta irreducible to *whether the firm actually beat* |

The third row settles it. The EPS-sign gate is **not a price factor** — it conditions on "did this firm
beat consensus," a quantity with zero contemporaneous market loading. The flat tertile *still went
negative.* So even restricting to **genuine positive fundamental surprises**, a 20-day-forward long
entry loses in flat tape and pays only in down-tape. The down-tape concentration is therefore **not**
back-door selection beta (proxy 1's story — already controlled for, since proxy 3 selects on a
fundamental, not on price). The surviving signature is stronger:

> In this universe/engine, a long-drift entry's forward return after a *real* earnings beat is
> conditional on the market **falling over the holding window** — oversold-down-tape mean-reversion, not
> firm-specific drift in any tape. **The beta enters through the holding-window forward return, not the
> entry selection.**

## Why the sector-neutral residual (#143) is predicted-dead FOR PEAD

A sector residual subtracts the same-night **sector-ETF** gap instead of the SPY gap — a *finer
entry-day co-movement control*. But the binding failure proxy 3 exposed is a **holding-window
forward-return conditionality**, not an entry-day co-movement failure. The sector residual is **strictly
weaker** than the EPS gate at attacking the surviving failure: it is another same-night co-movement
subtraction (like proxy 2, which already failed), aimed at the entry-day cross-section, while the
failure lives in the holding window. It cannot clear a bar that a strictly stronger, price-independent
control (the EPS sign) already failed. **Build #143 only if a *different* premise needs sector quotes —
never justify the spend on PEAD.**

Honest caveat (does not change the verdict): a sector residual is not *logically identical* to the EPS
gate — it could surface a name that beat sector-relative but not consensus-relative. For that to flip
the flat tertile positive, sector-relative *price* surprise would have to carry more forward-drift than
the *actual fundamental beat* — undocumented, and proxies 1–2 already showed price-residual surprise is
the **weaker** signal, not the stronger. If anyone insists on (b), the pre-registered bar is: flat-tape
20d lift ≥ +1.0% AND no down→flat sign-flip AND edge surviving in **flat and up** tape, at a screen run
**before** any θ tuning; a down-tape-only result is the same death, kill on sight.

## The 6th premise class — structurally beta-hedged

PEAD was the 5th class and the first *off* the regime-beta axis — and it died of regime-beta anyway. The
durable lesson: even a per-name, exogenous, dated event inherited SPY-direction beta through **every**
measurable surprise proxy, because the beta enters through the holding-window return, not the entry. So
the 6th class must attack the **return stream's market loading directly**, not just the entry's
cross-sectional cleanliness.

Steer (premise-class, not a config): **a market-neutral / relative-value premise where the position is
beta-hedged by construction** — a paired or spread long/short (e.g. event-conditioned
long-the-surpriser / short-its-sector-ETF, or a cross-sectional long-top / short-bottom dispersion
book), so the SPY-direction component is **netted out in the P&L** rather than hoped-away at entry.
Every long-only premise run so far inherits SPY through the holding window; the unexplored corner is
*structurally hedged exposure*. ^[inferred — the engine likely needs short-side support, a larger spend
than #143 but one that buys the whole class; route the actual mechanism design back to a fresh quant
consult before scoping.]

## Disposition

PEAD's long-only-drift form is **abandoned** (not a firewall death — no `config_hash` burned). The
slow-diffusion mechanism is formally untouched, but no entry proxy expressible on current data isolates
it — the citeable conclusion. Do not iterate the dead configs; do not burn #143 on PEAD; free the funnel
for a structurally beta-hedged premise (operator + fresh quant consult to scope the 6th class).

## Pages updated

[[pead]] (RECONSIDER_CLASS verdict, long-only form abandoned, #143-predicted-dead-for-PEAD note),
`index.md`, `wiki/log.md`.
</content>
