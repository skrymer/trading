#!/usr/bin/env python3
"""Which ENTRY characteristics discriminate winners from losers (and capture the right tail)?
A correlational proxy for condition contribution, from the 946-trade entryQuote data.
NOTE: gate conditions (RS>=70, %52wH<=25, ...) are all on the passing side by construction,
so this shows MARGINAL discriminating power WITHIN the passing range — not whether the gate
itself adds value (that needs a re-run ablation)."""
import json
from statistics import mean

trades = [t for t in json.load(open((__import__("sys").argv[1] if len(__import__("sys").argv)>1 else "/tmp/verify-promotion-minervini-vcp/inline-trades.json")))
          if t.get("entryQuote")]

def feat(t):
    q = t["entryQuote"]
    c = q["closePrice"]
    return {
        "RS": q.get("relativeStrengthPercentile"),
        "ADX": q.get("adx"),
        "pctFrom52wH": (q["high52Week"] - c) / q["high52Week"] * 100 if q.get("high52Week") else None,
        "pctAbove52wL": (c - q["low52Week"]) / q["low52Week"] * 100 if q.get("low52Week") else None,
        "ATRpct": q["atr"] / c * 100 if c else None,
        "distDonHi": (q["donchianUpperBand"] - c) / c * 100 if q.get("donchianUpperBand") and c else None,
    }

def buckets(name, edges, labels):
    print(f"\n=== {name} ===")
    print(f"{'bucket':<16}{'n':>5}{'win%':>6}{'meanP/L':>9}{'total%':>8}{'top10-hits':>11}")
    top10 = set(id(t) for t in sorted(trades, key=lambda x: -x['profitPercentage'])[:10])
    top50 = set(id(t) for t in sorted(trades, key=lambda x: -x['profitPercentage'])[:50])
    for i in range(len(edges) - 1):
        lo, hi = edges[i], edges[i + 1]
        seg = [t for t in trades if (v := feat(t)[name]) is not None and lo <= v < hi]
        if not seg:
            continue
        w = sum(1 for t in seg if t['profitPercentage'] > 0)
        t10 = sum(1 for t in seg if id(t) in top10)
        t50 = sum(1 for t in seg if id(t) in top50)
        print(f"{labels[i]:<16}{len(seg):>5}{w/len(seg)*100:>5.0f}%{mean(t['profitPercentage'] for t in seg):>+8.1f}%"
              f"{sum(t['profitPercentage'] for t in seg):>+7.0f}%{f'{t10}/10,{t50}/50':>11}")

buckets("RS", [70, 80, 90, 95, 101], ["70-80", "80-90", "90-95", "95-100"])
buckets("ADX", [0, 20, 30, 40, 200], ["<20", "20-30", "30-40", ">40"])
buckets("pctFrom52wH", [0, 5, 10, 15, 26], ["<5% (at high)", "5-10%", "10-15%", "15-25%"])
buckets("pctAbove52wL", [30, 60, 100, 200, 10000], ["30-60%", "60-100%", "100-200%", ">200%"])
buckets("ATRpct", [0, 3, 5, 8, 100], ["<3% (calm)", "3-5%", "5-8%", ">8% (volatile)"])
buckets("distDonHi", [-100, 0.01, 1, 3, 100], ["at/above hi", "0-1%", "1-3%", ">3%"])
