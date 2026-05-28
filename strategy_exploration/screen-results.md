
## /strategy-screen sweep (2005-2015, post-V13 universe)

Started 2026-05-27T14:00:49+10:00. 16 candidates. Risk-per-trade = 1.25% (G1 floor = 0.125%).

| Candidate | Verdict | Pass/Fail | First failure | Edge | Sharpe | CAGR | maxDD | Notes |
|---|---|---|---|---|---|---|---|---|
| **BR1-s1** | PASS | 5/5 | - | 0.728% | 1.46 | 9.44% | 12.1% | 7 windows, 622 trades |
| **BR1-s2** | FAIL | 4/5 | G4_gfc_stress | 0.98% | 2.15 | 14.61% | 12.29% | 7 windows, 597 trades |
| **BR1-s3** | FAIL | 4/5 | G4_gfc_stress | 1.024% | 1.77 | 12.13% | 15.69% | 7 windows, 606 trades |
| **BR2** | FAIL | 3/5 | G3_windows_positive | 0.791% | 1.51 | 25.59% | 25.73% | 7 windows, 1590 trades |
| **BR3-s1** | FAIL | 4/5 | G4_gfc_stress | 1.913% | 1.97 | 24.47% | 23.37% | 7 windows, 901 trades |
| **BR3-s2** | FAIL | 4/5 | G4_gfc_stress | 1.913% | 1.97 | 24.47% | 23.37% | 7 windows, 901 trades |
| **BR3-s3** | FAIL | 4/5 | G4_gfc_stress | 1.913% | 1.97 | 24.47% | 23.37% | 7 windows, 901 trades |
| **MO3-s1** | FAIL | 4/5 | G4_gfc_stress | 0.751% | 1.52 | 23.2% | 26.57% | 7 windows, 1269 trades |
| **MO3-s2** |  | /0 |  |  |  |  |  |  |
| **MO3-s3** | FAIL | 4/5 | G4_gfc_stress | 0.947% | 1.83 | 28.96% | 20.8% | 7 windows, 1276 trades |
| **MR3-s1** | PASS | 5/5 | - | 0.284% | 2.29 | 36.77% | 17.7% | 7 windows, 2899 trades |
| **MR3-s2** | PASS | 5/5 | - | 0.284% | 2.29 | 36.77% | 17.7% | 7 windows, 2899 trades |
| **MR3-s3** | PASS | 5/5 | - | 0.284% | 2.29 | 36.77% | 17.7% | 7 windows, 2899 trades |
| **VZ3-s1** | PASS | 5/5 | - | 0.657% | 2.42 | 34.4% | 11.92% | 7 windows, 1831 trades |
| **VZ3-s2** | PASS | 5/5 | - | 0.684% | 2.39 | 34.47% | 11.92% | 7 windows, 1848 trades |
| **VZ3-s3** | PASS | 5/5 | - | 0.698% | 2.52 | 36.97% | 11.92% | 7 windows, 1849 trades |

Sweep complete 2026-05-27T19:45:19+10:00

## /strategy-screen regime-gated re-fire (marketUptrend prepended)

Started 2026-05-27T20:10:48+10:00. Per quant: BR1-s2/s3 + MO3-s3 most likely to recover with absolute regime gate.

| Candidate | Verdict | Pass/Fail | First failure | Edge | Sharpe | CAGR | maxDD | Notes |
|---|---|---|---|---|---|---|---|---|
| **BR1-s2-regime** | FAIL | 4/5 | G4_gfc_stress | 0.98% | 2.15 | 14.61% | 12.29% | 7 windows, 597 trades |
| **BR1-s3-regime** | FAIL | 4/5 | G4_gfc_stress | 1.024% | 1.77 | 12.13% | 15.69% | 7 windows, 606 trades |
| **MO3-s3-regime** | FAIL | 4/5 | G4_gfc_stress | 0.99% | 1.53 | 19.17% | 24.5% | 7 windows, 889 trades |

Regime-gated re-fire complete 2026-05-27T21:24:43+10:00
