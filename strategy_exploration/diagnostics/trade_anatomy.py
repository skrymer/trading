#!/usr/bin/env python3
"""Characterize the 946 Track-1 trades (25y, EX-ATR20, spyTrendUp): winners vs losers,
clustering by year, and market-breadth/regime context at entry."""
import json, urllib.request
from statistics import mean, median

trades = json.load(open((__import__("sys").argv[1] if len(__import__("sys").argv)>1 else "/tmp/verify-promotion-minervini-vcp/inline-trades.json")))
# market breadth by date
breq = urllib.request.Request("http://localhost:9080/udgaard/api/breadth/market-daily",
                              headers={"X-API-Key": "changeme"})
brows = json.load(urllib.request.urlopen(breq))
breadth = {r["quoteDate"]: r for r in brows}
bdates = sorted(breadth)

import bisect
def breadth_on(d):
    i = bisect.bisect_right(bdates, d) - 1   # most recent breadth row <= entry date
    return breadth[bdates[i]] if i >= 0 else None

def pct(x): return f"{x:5.1f}%"

W = [t for t in trades if t["profitPercentage"] > 0]
L = [t for t in trades if t["profitPercentage"] <= 0]
print(f"=== OVERALL ({len(trades)} trades) ===")
print(f"win rate {len(W)/len(trades)*100:.1f}%  | mean P/L {mean(t['profitPercentage'] for t in trades):+.2f}%  median {median(t['profitPercentage'] for t in trades):+.2f}%")
gp = sum(t['profitPercentage'] for t in W); gl = -sum(t['profitPercentage'] for t in L)
print(f"avg WIN {mean(t['profitPercentage'] for t in W):+.2f}%  avg LOSS {mean(t['profitPercentage'] for t in L):+.2f}%  payoff {mean(t['profitPercentage'] for t in W)/-mean(t['profitPercentage'] for t in L):.2f}x")
print(f"hold days: win {mean(t['tradingDays'] for t in W):.0f}  loss {mean(t['tradingDays'] for t in L):.0f}")

print("\n=== BY YEAR (clustering) ===")
print(f"{'yr':>4}{'n':>5}{'win%':>6}{'totP/L%':>9}{'meanP/L':>9}  entry-breadth(mean)")
years = sorted({t['startDate'][:4] for t in trades})
for y in years:
    ty = [t for t in trades if t['startDate'][:4] == y]
    w = sum(1 for t in ty if t['profitPercentage'] > 0)
    bvals = [breadth_on(t['startDate'])['breadthPercent'] for t in ty if breadth_on(t['startDate'])]
    tot = sum(t['profitPercentage'] for t in ty)
    bar = '#' * int(w/len(ty)*20)
    print(f"{y:>4}{len(ty):>5}{w/len(ty)*100:>5.0f}%{tot:>8.0f}%{mean(t['profitPercentage'] for t in ty):>+8.1f}%  br={mean(bvals):4.1f}  {bar}")

print("\n=== MARKET BREADTH AT ENTRY: winners vs losers ===")
def bbucket(t):
    b = breadth_on(t['startDate'])
    return b['breadthPercent'] if b else None
for lo, hi, lbl in [(0,30,'breadth <30 (narrow)'),(30,45,'breadth 30-45'),(45,60,'breadth 45-60'),(60,200,'breadth >60 (broad)')]:
    seg = [t for t in trades if (bb:=bbucket(t)) is not None and lo <= bb < hi]
    if seg:
        w = sum(1 for t in seg if t['profitPercentage'] > 0)
        print(f"  {lbl:<22} n={len(seg):4d}  win%={w/len(seg)*100:4.0f}  meanP/L={mean(t['profitPercentage'] for t in seg):+5.1f}%  total={sum(t['profitPercentage'] for t in seg):+6.0f}%")

print("\n=== BREADTH EMA10 (regime trend) AT ENTRY ===")
for lo, hi, lbl in [(0,40,'ema10 <40'),(40,50,'ema10 40-50'),(50,60,'ema10 50-60'),(60,200,'ema10 >60')]:
    seg=[t for t in trades if (b:=breadth_on(t['startDate'])) and lo<=b['ema10']<hi]
    if seg:
        w=sum(1 for t in seg if t['profitPercentage']>0)
        print(f"  {lbl:<14} n={len(seg):4d}  win%={w/len(seg)*100:4.0f}  meanP/L={mean(t['profitPercentage'] for t in seg):+5.1f}%  total={sum(t['profitPercentage'] for t in seg):+6.0f}%")

print("\n=== RIGHT TAIL: top 15 trades by P/L% ===")
print(f"{'date':>11}{'sym':>7}{'sec':>5}{'P/L%':>8}{'hold':>5}{'RSpct':>7}  entry-breadth")
for t in sorted(trades, key=lambda x: -x['profitPercentage'])[:15]:
    b=breadth_on(t['startDate']); eq=t.get('entryQuote') or {}
    print(f"{t['startDate']:>11}{t['stockSymbol']:>7}{t.get('sector','?'):>5}{t['profitPercentage']:>+7.1f}%{t['tradingDays']:>5}{eq.get('relativeStrengthPercentile',0):>6.0f}  br={b['breadthPercent']:.0f} ema10={b['ema10']:.0f}")

print("\n=== concentration: how much of total P/L is in the top trades? ===")
allpl = sorted((t['profitPercentage'] for t in trades), reverse=True)
tot = sum(allpl)
for k in [5,10,20,50]:
    print(f"  top {k:3d} trades = {sum(allpl[:k]):+7.0f}% of {tot:+.0f}% total ({sum(allpl[:k])/tot*100:.0f}%)")
