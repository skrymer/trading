#!/usr/bin/env python3
"""Regenerate the frozen condition-screen SANITY-SWEEP universe (sanity-universe-v1.json).

This universe is for the /condition-screen WHOLE-LIBRARY SANITY SWEEP only — a rare, broad,
diagnostic pass across every registered entry condition. Normal single-condition design-time
screens use the FULL STOCK universe (the skill default); do NOT use this list for those.

Quant-approved 2026-05-31. The reduced universe widens date-clustered SEs and is therefore
ANTI-CONSERVATIVE for the ARS test (fragile conditions are MORE likely to pass) — it is a coarse
reject-net, never validation. See REFERENCE.md "Library sanity-sweep mode".

Construction (quant spec):
  - >=250 symbols (we target 300). NOT pure large-cap.
  - Sector-proportional across all sectors.
  - Liquidity tiers 50/30/20 large/mid/small by avg dollar-volume (close*volume), within sector.
  - Price-diverse (pick spread across avg close within each sector+tier bucket).
  - ~18% delisted-in-window names (survivorship mitigation — the most important deviation).
  - A 50-symbol order-block sub-universe (stratified subset) for the OB conditions, which are
    pathologically slow under the auto-sweep even here (aboveBearishOrderBlock -> defer to G13).
  - Deterministic: fixed seed so the list is reproducible/freezable.

The selection/random sequence below is byte-for-byte the algorithm that produced the canonical
v1 list the documented sweep actually ran on — do not "clean it up", or the random stream shifts
and the frozen symbols change.

Provenance SQL (run against the udgaard trading DB; >=250 in-window bars):

  SELECT q.stock_symbol,
         COALESCE(NULLIF(s.sector,''),'NA') sector,
         (s.delisting_date IS NOT NULL AND s.delisting_date < DATE '2021-01-01') delisted_inwin,
         count(*) bars,
         round(avg(q.close_price)::numeric,2) avg_close,
         round(avg(q.close_price*q.volume)::numeric,0) avg_dollar_vol
  FROM stock_quotes q
  JOIN symbols y ON y.symbol=q.stock_symbol AND y.asset_type='STOCK'
  JOIN stocks  s ON s.symbol=q.stock_symbol
  WHERE q.quote_date >= DATE '2000-01-01' AND q.quote_date < DATE '2021-01-01'
  GROUP BY 1,2,3
  HAVING count(*) >= 250;

Save that result (pipe-delimited, no header) to symbol-stats.tsv next to this script, then run:
  python3 build-sanity-universe.py
to re-emit sanity-universe-v1.json. Same seed + same DB snapshot => identical list.
"""
import csv, json, random, collections, os, sys

SEED = 20260531
TOTAL = 300
DELISTED_FRAC = 0.18
OB_COUNT = 50

HERE = os.path.dirname(os.path.abspath(__file__))
STATS = os.path.join(HERE, "symbol-stats.tsv")
OUT = os.path.join(HERE, "sanity-universe-v1.json")


def pick_spread(cands, n, key):
    """Pick n items spread evenly across cands sorted by key (price diversity). Deterministic."""
    if n <= 0 or not cands:
        return []
    cands = sorted(cands, key=lambda r: r[key])
    if n >= len(cands):
        return cands
    idx = [round(i * (len(cands) - 1) / (n - 1)) for i in range(n)] if n > 1 else [len(cands) // 2]
    seen, out = set(), []
    for i in idx:
        while i in seen:
            i = (i + 1) % len(cands)
        seen.add(i)
        out.append(cands[i])
    return out


def build():
    random.seed(SEED)
    rows = []
    with open(STATS) as f:
        for sym, sector, delisted, bars, avg_close, advol in csv.reader(f, delimiter="|"):
            if sector == "NA":
                continue
            rows.append(dict(sym=sym, sector=sector, delisted=(delisted == "t"),
                             bars=int(bars), price=float(avg_close), advol=float(advol)))

    by_sector = {}
    for r in rows:
        by_sector.setdefault(r["sector"], []).append(r)
    grand = len(rows)

    targets = {s: max(8, round(TOTAL * len(v) / grand)) for s, v in by_sector.items()}
    cur = sum(targets.values())
    while cur > TOTAL:
        k = max(targets, key=lambda k: targets[k]); targets[k] -= 1; cur -= 1
    while cur < TOTAL:
        k = max(targets, key=lambda k: len(by_sector[k]) - targets[k]); targets[k] += 1; cur += 1

    # selection — deterministic, no random calls yet
    chosen = []
    for sector, grp in by_sector.items():
        tgt = targets[sector]
        g = sorted(grp, key=lambda r: r["advol"]); n = len(g); t1 = n // 3; t2 = 2 * n // 3
        small, mid, large = g[:t1], g[t1:t2], g[t2:]
        alloc = [("large", large, round(tgt * 0.5)), ("mid", mid, round(tgt * 0.3)),
                 ("small", small, tgt - round(tgt * 0.5) - round(tgt * 0.3))]
        for tier, bucket, k in alloc:
            chosen += [dict(r, tier=tier) for r in pick_spread(bucket, k, "price")]

    # survivorship: ensure ~18% delisted by swapping within same sector+tier.
    # del_pool MUST be built in by_sector insertion order, tiers [small, mid, large], and shuffled
    # in that key-insertion order — this fixes the exact random-call sequence.
    def cur_delisted():
        return sum(1 for r in chosen if r["delisted"])
    target_del = round(TOTAL * DELISTED_FRAC)
    chosen_syms = {r["sym"] for r in chosen}
    del_pool = {}
    for sector, grp in by_sector.items():
        g = sorted(grp, key=lambda r: r["advol"]); n = len(g); t1 = n // 3; t2 = 2 * n // 3
        for tier, bucket in [("small", g[:t1]), ("mid", g[t1:t2]), ("large", g[t2:])]:
            for r in bucket:
                if r["delisted"] and r["sym"] not in chosen_syms:
                    del_pool.setdefault((sector, tier), []).append(dict(r, tier=tier))
    for k in del_pool:
        random.shuffle(del_pool[k])

    non_del = [i for i, r in enumerate(chosen) if not r["delisted"]]
    random.shuffle(non_del)
    i = 0
    while cur_delisted() < target_del and i < len(non_del):
        idx = non_del[i]; i += 1
        key = (chosen[idx]["sector"], chosen[idx]["tier"])
        if del_pool.get(key):
            chosen[idx] = del_pool[key].pop()

    ob = sorted({r["sym"] for r in pick_spread(chosen, OB_COUNT, "advol")})
    return chosen, ob


def main():
    if not os.path.exists(STATS):
        sys.exit(f"missing {STATS} — run the provenance SQL first (see module docstring)")
    chosen, ob = build()

    def rec(r):
        return dict(symbol=r["sym"], sector=r["sector"], tier=r["tier"],
                    delistedInWindow=r["delisted"], avgPrice=round(r["price"], 2),
                    avgDollarVol=int(r["advol"]), bars=r["bars"])

    main_recs = sorted([rec(r) for r in chosen], key=lambda x: x["symbol"])
    doc = dict(
        name="condition-screen-sanity-universe-v1",
        purpose="Frozen reduced universe for the /condition-screen WHOLE-LIBRARY SANITY SWEEP only. "
                "NOT for normal single-condition screens (those use the full STOCK universe). "
                "Quant-approved 2026-05-31.",
        isUniverseDefault=False,
        window="2000-01-01..2021-01-01 (Block C cap)",
        seed=SEED,
        builtFrom="udgaard trading DB stocks+stock_quotes; symbols with >=250 in-window bars; "
                  "regenerate via build-sanity-universe.py",
        constraints=dict(
            minSymbols=250, mainCount=len(main_recs), orderBlockCount=len(ob),
            sectorProportional=True,
            liquidityTiers="50/30/20 large/mid/small by avg dollar-volume",
            priceDiverse=True,
            delistedFraction="~18% delisted-in-window (survivorship mitigation)",
            orderBlockHandling="OB conditions run on the 50-symbol orderBlock subset; "
                               "aboveBearishOrderBlock may still time out -> ARS deferred to firewall G13",
            inconclusiveGuardrail="sweep cell with dateCount(nDates)<~30 or median 1-2 firings/date "
                                  "= INCONCLUSIVE, not clean"),
        reducedUniverseNotice="REDUCED-UNIVERSE SANITY SWEEP - NOT A FULL-UNIVERSE RESULT. Widens "
            "date-clustered SEs -> anti-conservative for the ARS 2xSE test (fragile conditions MORE "
            "likely to pass). A clean result here is NOT validation; re-screen on the full universe "
            "or run the firewall before any tradability claim. Not edge estimates.",
        main=main_recs,
        orderBlock=ob)
    json.dump(doc, open(OUT, "w"), indent=2)
    nd = sum(1 for r in main_recs if r["delistedInWindow"])
    print(f"wrote {OUT}: {len(main_recs)} main, {len(ob)} OB, {nd} delisted ({nd/len(main_recs)*100:.0f}%)")


if __name__ == "__main__":
    main()
