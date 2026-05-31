#!/usr/bin/env python3
"""Tests for config_hash — the candidate fingerprint that anchors the data-mining interlock.

The hash projects a candidate request onto the G10 design-isolation freeze set and excludes
dates, so one candidate keeps a single hash across every firewall block. See ADR 0008.

Run: python3 test_config_hash.py
"""
import io
import json
import os
import tempfile
import unittest
from contextlib import redirect_stdout

import config_hash


def request_with(start="2000-01-01", end="2014-01-01", max_positions=15, risk=1.25):
    """A minimal validation template with a tunable surface."""
    return {
        "startDate": start,
        "endDate": end,
        "maxPositions": max_positions,
        "entryDelayDays": 1,
        "positionSizing": {
            "startingCapital": 10000,
            "sizer": {"type": "atrRisk", "riskPercentage": risk, "nAtr": 2.0},
            "leverageRatio": 1.0,
        },
        "entryStrategy": {"type": "custom", "conditions": [{"type": "pullback", "parameters": {"lookbackDays": 10}}]},
        "exitStrategy": {"type": "custom", "conditions": [{"type": "marketDowntrend"}]},
        "ranker": "SectorEdge",
    }


class ConfigHashExcludesDates(unittest.TestCase):
    def test_same_config_different_dates_hashes_identically(self):
        # Given two templates that differ ONLY in the date range
        block_a = request_with(start="2000-01-01", end="2014-01-01")
        block_b = request_with(start="2014-01-01", end="2021-06-30")

        # When each is hashed
        # Then the hash is identical — the candidate is the same across blocks
        self.assertEqual(config_hash.config_hash(block_a), config_hash.config_hash(block_b))


class ConfigHashSeparatesRealConfigs(unittest.TestCase):
    def test_changing_a_freeze_field_changes_the_hash(self):
        # Given two templates differing in a single freeze field (maxPositions)
        base = request_with(max_positions=15)
        tweaked = request_with(max_positions=10)

        # When each is hashed
        # Then the hashes differ — the interlock can tell the two configs apart
        self.assertNotEqual(config_hash.config_hash(base), config_hash.config_hash(tweaked))


class ConfigHashCli(unittest.TestCase):
    def test_cli_prints_hash_for_template_file(self):
        # Given a template written to disk (the form run-pipeline.sh's G10 autoconfirm will hash)
        req = request_with()
        fd, path = tempfile.mkstemp(suffix=".json")
        os.close(fd)
        with open(path, "w") as f:
            json.dump(req, f)

        # When the CLI is invoked on the file
        buf = io.StringIO()
        with redirect_stdout(buf):
            config_hash.main([path])
        os.unlink(path)

        # Then it prints exactly the library hash — the pipeline can trust it as the source of truth
        self.assertEqual(buf.getvalue().strip(), config_hash.config_hash(req))


if __name__ == "__main__":
    unittest.main()
