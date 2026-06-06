#!/usr/bin/env python3
"""Deflated-Sharpe flag assembly — the state-machine half of ADR 0014.

The engine owns the math (`RiskMetricsService.deflatedSharpe`, search-agnostic). This module owns
the *search* half: turning the global firewall-trial register (registry.collect_firewall_trials)
into the effective trial count and the cross-trial Sharpe variance the engine needs, plus the
itemized lineage list that is always published with the flag.

Phase 1 (this module) ships the conservative N_high endpoint only — `1 + (k-1)(1-ρ̄)` correlation
haircut (N_low, the red tier) is a fast follow that needs within-lineage return-series correlation.
"""
import math

# Stitched-OOS Sharpes are annualized by √252 (RiskMetricsService convention). The PSR/DSR formulae
# are per-observation and not scale-invariant, so we de-annualize before calling the engine.
TRADING_DAYS_PER_YEAR = 252


def lineage_clusters(trials):
    """Group firewall trials by lineage (dossier file) — the itemized list published with the flag.

    One dossier file = one candidate = one lineage = one cluster (ADR 0008's DISTINCT gate doubles
    as the trial-counter: a structurally-distinct successor is a new file). Returns one entry per
    lineage in first-seen order: {"lineage", "hashes", "count"}.
    """
    order = []
    by_lineage = {}
    for t in trials:
        lineage = t["lineage"]
        if lineage not in by_lineage:
            by_lineage[lineage] = []
            order.append(lineage)
        by_lineage[lineage].append(t["hash"])
    return [{"lineage": name, "hashes": by_lineage[name], "count": len(by_lineage[name])}
            for name in order]


def n_high(trials):
    """The conservative effective-N endpoint: the integer count of distinct lineages.

    Within-lineage variants are near-clones, not independent shots, so they collapse into their
    one lineage and do not inflate the count.
    """
    return len(lineage_clusters(trials))


def trial_sharpe_variance(sharpes):
    """Population variance (÷N) of the per-trial Sharpe scalars — the `E[max]` null's spread input.

    Matches the engine's variance convention. The Sharpes must be passed in the SAME units the
    engine expects (per-observation): de-annualize an annualized Sharpe variance by dividing by
    periodsPerYear before/while assembling these. A single trial has no spread → 0.
    """
    if not sharpes:
        return 0.0
    mean = sum(sharpes) / len(sharpes)
    return sum((s - mean) ** 2 for s in sharpes) / len(sharpes)


def build_request(observed_sharpe_annualized, n_eff, trial_sharpes_annualized,
                  skew, kurtosis, n_obs, periods_per_year=TRADING_DAYS_PER_YEAR):
    """Assemble the search-agnostic engine payload (POST /api/risk/deflated-sharpe).

    De-annualizes the candidate's own Sharpe and the cross-trial Sharpe variance into the
    per-observation units the engine expects (Sharpe ÷√periods, variance ÷periods). `n_eff` is the
    effective trial count (phase 1: N_high). `skew`/`kurtosis` are the candidate's own return-shape
    moments (default Gaussian when its firewall result does not carry them).
    """
    factor = math.sqrt(periods_per_year)
    per_obs = [s / factor for s in trial_sharpes_annualized]
    return {
        "observedSharpe": observed_sharpe_annualized / factor,
        "nEff": n_eff,
        "trialSharpeVariance": trial_sharpe_variance(per_obs),
        "skew": skew,
        "kurtosis": kurtosis,
        "nObs": n_obs,
    }
