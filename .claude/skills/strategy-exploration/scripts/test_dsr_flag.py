#!/usr/bin/env python3
"""Tests for the Deflated-Sharpe flag assembly — the state-machine half of ADR 0014.

The flag deflates a survivor's firewall Sharpe by the expected maximum over the *effective* trial
count. Phase 1 ships the conservative N_high endpoint only (one dossier file = one lineage =
one cluster); within-lineage variants (±1 G13 neighbours, sizer/exit tweaks) are near-clones and
must NOT inflate the count. The itemized lineage list is always published (hidden N is the sin).

Run: python3 test_dsr_flag.py
"""
import unittest

import dsr_flag


class NHighCountsDistinctLineages(unittest.TestCase):
    def test_n_high_is_the_number_of_distinct_lineage_files(self):
        # Given firewall trials drawn from three distinct dossier files
        trials = [
            {"lineage": "MR4", "hash": "a1", "verdict": "REJECTED"},
            {"lineage": "MR5", "hash": "b1", "verdict": "TRADABLE"},
            {"lineage": "VZ3", "hash": "c1", "verdict": "NEAR_MISS"},
        ]

        # When the conservative N_high endpoint is computed
        clusters = dsr_flag.lineage_clusters(trials)

        # Then it equals the distinct-lineage count
        self.assertEqual(dsr_flag.n_high(trials), 3)
        self.assertEqual(len(clusters), 3)

    def test_within_lineage_variants_do_not_inflate_n_high(self):
        # Given one lineage that fired three near-clone variants (sizer/exit tweaks, ±1 neighbours)
        # plus one unrelated lineage
        trials = [
            {"lineage": "MR4", "hash": "a1", "verdict": "REJECTED"},
            {"lineage": "MR4", "hash": "a2", "verdict": "REJECTED"},
            {"lineage": "MR4", "hash": "a3", "verdict": "NEAR_MISS"},
            {"lineage": "MR5", "hash": "b1", "verdict": "TRADABLE"},
        ]

        # When N_high is computed, Then the three variants collapse into their one lineage
        self.assertEqual(dsr_flag.n_high(trials), 2)
        mr4 = next(c for c in dsr_flag.lineage_clusters(trials) if c["lineage"] == "MR4")
        self.assertEqual(mr4["count"], 3)
        self.assertEqual(mr4["hashes"], ["a1", "a2", "a3"])


class TrialSharpeVariance(unittest.TestCase):
    def test_population_variance_of_the_trial_sharpes(self):
        # Given the per-trial Sharpe scalars observed across the firewall trials
        sharpes = [0.1, 0.2, 0.3]

        # When the cross-trial variance is computed (population, ÷N — matches the engine convention)
        var = dsr_flag.trial_sharpe_variance(sharpes)

        # Then it is the mean squared deviation about the mean (0.2)
        self.assertAlmostEqual(var, 0.02 / 3, places=12)

    def test_single_trial_has_zero_variance(self):
        # Given a lone trial, there is no spread to deflate against
        self.assertEqual(dsr_flag.trial_sharpe_variance([0.2]), 0.0)


class BuildEngineRequest(unittest.TestCase):
    def test_de_annualizes_sharpes_to_per_observation_units(self):
        # Given an annualized survivor Sharpe and the annualized trial Sharpes from the register,
        # measured on a daily (252/yr) stitched-OOS series
        request = dsr_flag.build_request(
            observed_sharpe_annualized=1.5,
            n_eff=8,
            trial_sharpes_annualized=[1.0, 1.5, 2.0],
            skew=0.0,
            kurtosis=3.0,
            n_obs=5000,
        )

        # Then the engine payload carries PER-OBSERVATION Sharpe (÷√252) and variance (÷252)
        self.assertAlmostEqual(request["observedSharpe"], 1.5 / (252 ** 0.5), places=9)
        self.assertAlmostEqual(request["trialSharpeVariance"], (0.5 / 3) / 252, places=12)
        self.assertEqual(request["nEff"], 8)
        self.assertEqual(request["nObs"], 5000)
        self.assertEqual(request["skew"], 0.0)
        self.assertEqual(request["kurtosis"], 3.0)


if __name__ == "__main__":
    unittest.main()
