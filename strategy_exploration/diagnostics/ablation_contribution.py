#!/usr/bin/env python3
"""Condition-contribution table: each ablation vs baseline, with monster retention."""
import json

BASE_TRADES = "/tmp/verify-promotion-minervini-vcp/inline-trades.json"
BASE_RESP = "/tmp/verify-promotion-minervini-vcp/promoted-resp.json"

def load(p):
    try: return json.load(open(p))
    except Exception: return None

base_tr = load(BASE_TRADES)
base = load(BASE_RESP)
# baseline top-50 monsters keyed by (entry date, symbol)
top50 = set((t["startDate"], t["stockSymbol"]) for t in sorted(base_tr, key=lambda x: -x["profitPercentage"])[:50])
top10 = set((t["startDate"], t["stockSymbol"]) for t in sorted(base_tr, key=lambda x: -x["profitPercentage"])[:10])

def metrics(resp, trades):
    rm = resp.get("riskMetrics") or {}
    keys = set((t["startDate"], t["stockSymbol"]) for t in trades) if trades else set()
    return {
        "trades": resp.get("totalTrades"),
        "cagr": resp.get("cagr", 0),
        "edge": resp.get("edge", 0),
        "win": resp.get("winRate", 0) * 100,
        "pf": resp.get("profitFactor", 0),
        "calmar": rm.get("calmarRatio"),
        "sharpe": rm.get("sharpeRatio"),
        "m50": len(top50 & keys),
        "m10": len(top10 & keys),
    }

RUNS = [
    ("BASELINE (10 conds)", BASE_RESP, BASE_TRADES),
    ("+ADX(30,100)", "/tmp/abl-adxadd-resp.json", "/tmp/abl-adxadd-trades.json"),
    ("-spyTrendUp", "/tmp/abl-drop-spyTrendUp-resp.json", "/tmp/abl-drop-spyTrendUp-trades.json"),
    ("-movingAverageStack", "/tmp/abl-drop-movingAverageStack-resp.json", "/tmp/abl-drop-movingAverageStack-trades.json"),
    ("-movingAverageRising", "/tmp/abl-drop-movingAverageRising-resp.json", "/tmp/abl-drop-movingAverageRising-trades.json"),
    ("-percentFrom52WeekHigh", "/tmp/abl-drop-percentFrom52WeekHigh-resp.json", "/tmp/abl-drop-percentFrom52WeekHigh-trades.json"),
    ("-percentFrom52WeekLow", "/tmp/abl-drop-percentFrom52WeekLow-resp.json", "/tmp/abl-drop-percentFrom52WeekLow-trades.json"),
    ("-relativeStrengthPercentile", "/tmp/abl-drop-relativeStrengthPercentile-resp.json", "/tmp/abl-drop-relativeStrengthPercentile-trades.json"),
    ("-narrowingRange", "/tmp/abl-drop-narrowingRange-resp.json", "/tmp/abl-drop-narrowingRange-trades.json"),
    ("-volumeDryUp", "/tmp/abl-drop-volumeDryUp-resp.json", "/tmp/abl-drop-volumeDryUp-trades.json"),
    ("-priceNearDonchianHigh", "/tmp/abl-drop-priceNearDonchianHigh-resp.json", "/tmp/abl-drop-priceNearDonchianHigh-trades.json"),
    ("-volumeAboveAverage", "/tmp/abl-drop-volumeAboveAverage-resp.json", "/tmp/abl-drop-volumeAboveAverage-trades.json"),
]

def fc(x, p=1):
    return "  n/a" if x is None else f"{x:.{p}f}"

print(f'{"variant":<30}{"trades":>7}{"CAGR":>7}{"edge":>6}{"win%":>6}{"PF":>5}{"Calmar":>7}{"Sharpe":>7}{"mon50":>7}{"mon10":>6}')
for label, rp, tp in RUNS:
    resp = load(rp); trades = load(tp)
    if resp is None or resp.get("totalTrades") is None:
        print(f'{label:<30}  — FAILED (heap OOM): dropping this explodes the candidate set = PRIMARY THINNER')
        continue
    m = metrics(resp, trades)
    print(f'{label:<30}{m["trades"]:>7}{fc(m["cagr"]):>7}{fc(m["edge"],2):>6}{fc(m["win"]):>6}{fc(m["pf"],1):>5}'
          f'{fc(m["calmar"],2):>7}{fc(m["sharpe"],2):>7}{m["m50"]:>5}/50{m["m10"]:>4}/10')
print("\nmon50/mon10 = how many of the baseline's top-50 / top-10 monster winners the variant still captures.")
