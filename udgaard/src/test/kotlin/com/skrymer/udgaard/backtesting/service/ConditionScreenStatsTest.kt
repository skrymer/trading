package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

private const val EPSILON = 1e-6

class ConditionScreenStatsTest {
  private fun quote(date: String, close: Double) =
    StockQuote(symbol = "TEST", date = LocalDate.parse(date), closePrice = close)

  @Test
  fun `forward return is anchored to the fill bar, not the signal bar`() {
    // Given a symbol whose closes are t0=100, t1(fill)=110, t2=120, t3(target)=132
    // and entryDelayDays=1 so a signal at t0 fills at t1
    val quotes =
      listOf(
        quote("2020-01-01", 100.0),
        quote("2020-01-02", 110.0),
        quote("2020-01-03", 120.0),
        quote("2020-01-06", 132.0),
      )

    // When a signal fires at index 0 and we measure the N=2 forward return
    val fr =
      ConditionScreenStats.signalForwardReturn(
        quotes = quotes,
        signalIndex = 0,
        entryDelayDays = 1,
        horizons = listOf(2),
      )

    // Then the return is close[fill+2]/close[fill] - 1 = 132/110 - 1 = 0.20 (NOT 132/100 - 1)
    assertEquals(0.20, fr.returnsByHorizon.getValue(2)!!, EPSILON)
    // And the signal->fill gap is close[fill]/close[signal] - 1 = 110/100 - 1 = 0.10
    assertEquals(0.10, fr.fillGap!!, EPSILON)
  }

  @Test
  fun `forward return is null when the horizon target bar is past the end of the series`() {
    // Given only 3 bars and a signal at index 0 with entryDelay=1 (fill at index 1)
    val quotes =
      listOf(
        quote("2020-01-01", 100.0),
        quote("2020-01-02", 110.0),
        quote("2020-01-03", 120.0),
      )

    // When we ask for N=1 (target index 1+1=2, exists) and N=5 (target index 6, does not exist)
    val fr =
      ConditionScreenStats.signalForwardReturn(
        quotes = quotes,
        signalIndex = 0,
        entryDelayDays = 1,
        horizons = listOf(1, 5),
      )

    // Then the reachable horizon resolves and the unreachable one is null (a dropped signal at N=5)
    assertEquals(120.0 / 110.0 - 1, fr.returnsByHorizon.getValue(1)!!, EPSILON)
    assertNull(fr.returnsByHorizon.getValue(5))
  }

  private fun signal(symbol: String, date: String, returnAt10: Double?) =
    SignalForwardReturn(
      symbol = symbol,
      signalDate = LocalDate.parse(date),
      fillGap = 0.0,
      returnsByHorizon = mapOf(10 to returnAt10),
    )

  @Test
  fun `distribution summarises mean, median, hit rate and counts dropped signals at a horizon`() {
    // Given four fired signals with N=10 returns [+0.10, -0.05, +0.20, -0.05]
    // plus one signal that fired but has no N=10 return (dropped: insufficient forward window)
    val signals =
      listOf(
        signal("A", "2020-01-02", 0.10),
        signal("B", "2020-01-03", -0.05),
        signal("C", "2020-01-06", 0.20),
        signal("D", "2020-01-07", -0.05),
        signal("E", "2020-01-08", null),
      )

    // When we summarise the distribution at N=10
    val dist = ConditionScreenStats.distributionAt(signals, horizon = 10)

    // Then counts separate the measured sample from the dropped signal
    assertEquals(4, dist.nSignals)
    assertEquals(1, dist.nDropped)
    // And mean = (0.10 - 0.05 + 0.20 - 0.05) / 4 = 0.05
    assertEquals(0.05, dist.mean, EPSILON)
    // And median of [-0.05, -0.05, 0.10, 0.20] = (-0.05 + 0.10) / 2 = 0.025
    assertEquals(0.025, dist.median, EPSILON)
    // And hit rate = 2 positive / 4 = 0.5
    assertEquals(0.5, dist.hitRate, EPSILON)
    // And nDates counts distinct signal dates in the measured sample (all four differ)
    assertEquals(4, dist.nDates)
  }

  @Test
  fun `clustered estimate averages per-date means first to neutralise same-day correlation`() {
    // Given five signals across two dates (same-day signals are not independent observations):
    //   2020-01-02: [+0.10, +0.20]            -> date mean +0.15
    //   2020-01-03: [-0.05, -0.05, 0.00]      -> date mean -0.033333...
    val signals =
      listOf(
        signal("A", "2020-01-02", 0.10),
        signal("B", "2020-01-02", 0.20),
        signal("C", "2020-01-03", -0.05),
        signal("D", "2020-01-03", -0.05),
        signal("E", "2020-01-03", 0.00),
      )

    // When we compute the date-clustered estimate at N=10
    val est = ConditionScreenStats.clusteredEstimateAt(signals, horizon = 10)

    // Then the point estimate is the mean of the two DATE means, not the pooled mean of five signals
    // = (0.15 + (-0.033333...)) / 2 = 0.0583333...
    assertEquals(0.0583333, est.clusteredMean, EPSILON)
    // And the effective sample size is the number of dates, not signals
    assertEquals(2, est.nDates)
    // And the standard error is the sample std of date means / sqrt(nDates)
    // date means [0.15, -0.033333]: sample std = 0.1296362, SE = 0.1296362 / sqrt(2) = 0.0916667
    assertEquals(0.0916667, est.stdError, EPSILON)
  }

  @Test
  fun `lift is the condition clustered mean and hit rate minus the universe baseline`() {
    // Given the condition's fired signals (two dates): mean +0.15, hit rate 1.0
    val condition =
      ConditionScreenStats.summariseAt(
        listOf(
          signal("A", "2020-01-02", 0.10),
          signal("B", "2020-01-03", 0.20),
        ),
        horizon = 10,
      )
    // And the universe all-bars baseline over the same window:
    //   2020-01-02: [+0.05, -0.03] -> date mean +0.01 ; 2020-01-03: [+0.05] -> +0.05
    //   clustered mean = (0.01 + 0.05)/2 = 0.03 ; hit rate = 2 positive / 3 = 0.66667
    val universe =
      ConditionScreenStats.summariseAt(
        listOf(
          signal("X", "2020-01-02", 0.05),
          signal("Y", "2020-01-02", -0.03),
          signal("Z", "2020-01-03", 0.05),
        ),
        horizon = 10,
      )

    // When we compute the lift
    val lift = ConditionScreenStats.lift(condition, universe)

    // Then mean lift = 0.15 - 0.03 = 0.12 (on the date-clustered means)
    assertEquals(0.12, lift.meanLift, EPSILON)
    // And hit-rate lift = 1.0 - 0.66667 = 0.33333
    assertEquals(0.3333333, lift.hitRateLift, EPSILON)
  }

  private fun key(symbol: String, date: String) = SignalKey(symbol, LocalDate.parse(date))

  @Test
  fun `jaccard overlap is computed per year and pooled over symbol-date firing sets`() {
    // Given a condition and a reference condition firing on these (symbol, date) bars:
    //   2020: condition {(A,01-02),(A,01-03),(B,01-02)}  reference {(A,01-02),(B,01-02),(C,01-05)}
    //   2021: condition {(A,01-04)}                       reference {}
    val condition =
      setOf(
        key("A", "2020-01-02"),
        key("A", "2020-01-03"),
        key("B", "2020-01-02"),
        key("A", "2021-01-04"),
      )
    val reference =
      setOf(
        key("A", "2020-01-02"),
        key("B", "2020-01-02"),
        key("C", "2020-01-05"),
      )

    // When we compute Jaccard overlap
    val overlap = ConditionScreenStats.jaccardOverlap(condition, reference)

    // Then 2020 = |∩|/|∪| = 2/4 = 0.5 ; 2021 = 0/1 = 0.0
    assertEquals(0.5, overlap.byYear.getValue(2020), EPSILON)
    assertEquals(0.0, overlap.byYear.getValue(2021), EPSILON)
    // And pooled across all years = 2/5 = 0.4
    assertEquals(0.4, overlap.pooled!!, EPSILON)
  }

  @Test
  fun `jaccard pooled is null when both firing sets are empty`() {
    // Given nothing fired in either set (no basis to compare)
    val overlap = ConditionScreenStats.jaccardOverlap(emptySet(), emptySet())

    // Then pooled is null (undefined), not a misleading 0.0
    assertNull(overlap.pooled)
  }

  @Test
  fun `spy regime breakdown reports firing rate and lift per tertile, exposing regime-coupled edge`() {
    // Given six trading dates split into SPY-20d-return tertiles (2 dates each):
    //   down {01-01,01-02}  flat {01-03,01-06}  up {01-07,01-08}
    val spy20d =
      mapOf(
        LocalDate.parse("2020-01-01") to -0.30,
        LocalDate.parse("2020-01-02") to -0.10,
        LocalDate.parse("2020-01-03") to -0.02,
        LocalDate.parse("2020-01-06") to 0.05,
        LocalDate.parse("2020-01-07") to 0.12,
        LocalDate.parse("2020-01-08") to 0.30,
      )
    // Universe = every eligible bar (2 per date). Down-tertile universe mean 0.0; up-tertile 0.10.
    val universe =
      listOf(
        signal("U", "2020-01-01", 0.0),
        signal("U", "2020-01-01", 0.0),
        signal("U", "2020-01-02", 0.0),
        signal("U", "2020-01-02", 0.0),
        signal("U", "2020-01-03", 0.0),
        signal("U", "2020-01-03", 0.0),
        signal("U", "2020-01-06", 0.0),
        signal("U", "2020-01-06", 0.0),
        signal("U", "2020-01-07", 0.10),
        signal("U", "2020-01-07", 0.10),
        signal("U", "2020-01-08", 0.10),
        signal("U", "2020-01-08", 0.10),
      )
    // Condition fires once in the down tertile (+0.02) and twice in the up tertile (+0.05 each)
    val condition =
      listOf(
        signal("C", "2020-01-01", 0.02),
        signal("C", "2020-01-07", 0.05),
        signal("C", "2020-01-08", 0.05),
      )

    // When we break firing + lift down by SPY regime
    val regime = ConditionScreenStats.spyRegimeBreakdown(spy20d, condition, universe, horizon = 10)

    // Then firing rate = condition signals / eligible bars within each tertile (4 bars per tertile)
    assertEquals(0.25, regime.down.firingRate, EPSILON) // 1 of 4
    assertEquals(0.0, regime.flat.firingRate, EPSILON) // 0 of 4
    assertEquals(0.5, regime.up.firingRate, EPSILON) // 2 of 4
    // And the lift FLIPS SIGN across regimes — the regime-coupling tell:
    // down lift = 0.02 - 0.0 = +0.02 ; up lift = 0.05 - 0.10 = -0.05
    assertEquals(0.02, regime.down.meanLift!!, EPSILON)
    assertEquals(-0.05, regime.up.meanLift!!, EPSILON)
    // And a tertile with no condition signals has null lift (firing rate still meaningful)
    assertNull(regime.flat.meanLift)
  }

  @Test
  fun `firing rate per year is condition signals over eligible bars in that calendar year`() {
    // Given a universe of eligible bars (3 in 2020, 2 in 2021) and condition firings (1 in 2020, 2 in 2021)
    val universe =
      listOf(
        signal("U", "2020-03-01", 0.0),
        signal("U", "2020-06-01", 0.0),
        signal("U", "2020-09-01", 0.0),
        signal("U", "2021-03-01", 0.0),
        signal("U", "2021-06-01", 0.0),
      )
    val condition =
      listOf(
        signal("C", "2020-03-01", 0.0),
        signal("C", "2021-03-01", 0.0),
        signal("C", "2021-06-01", 0.0),
      )

    // When we compute firing rate per year
    val byYear = ConditionScreenStats.firingByYear(condition, universe).associateBy { it.year }

    // Then 2020 = 1/3 firing on 1 signal; 2021 = 2/2 firing on 2 signals
    assertEquals(1, byYear.getValue(2020).signals)
    assertEquals(3, byYear.getValue(2020).eligibleBars)
    assertEquals(0.3333333, byYear.getValue(2020).firingRate, EPSILON)
    assertEquals(1.0, byYear.getValue(2021).firingRate, EPSILON)
  }

  @Test
  fun `discrete parameter sweeps to adjacent allowed values around the center`() {
    // Given a discrete option set {5,10,20,50,100,200} and center 10
    val variants = ConditionScreenStats.parameterVariants(center = 10.0, options = listOf(5.0, 10.0, 20.0, 50.0, 100.0, 200.0))

    // Then the neighbours are the immediately adjacent allowed values (not 10±1)
    assertEquals(listOf(5.0, 20.0), variants)
  }

  @Test
  fun `discrete parameter at the edge of the option set has one neighbour`() {
    // Given center 200 at the top of the allowed set
    val variants = ConditionScreenStats.parameterVariants(center = 200.0, options = listOf(5.0, 10.0, 20.0, 50.0, 100.0, 200.0))

    // Then only the lower neighbour exists
    assertEquals(listOf(100.0), variants)
  }

  @Test
  fun `continuous parameter without an option set sweeps plus and minus ten percent`() {
    // Given a continuous tunable (no allowed-value set) centered at 2.0
    val variants = ConditionScreenStats.parameterVariants(center = 2.0, options = null)

    // Then it sweeps ±10% around the center
    assertEquals(2, variants.size)
    assertEquals(1.8, variants[0], EPSILON)
    assertEquals(2.2, variants[1], EPSILON)
  }

  @Test
  fun `discrete parameter centre is matched to its option grid by epsilon, not bit-equality`() {
    // Given a centre that carries floating-point drift (0.05 + 0.05 != 0.1 bit-for-bit)
    val drifted = 0.05 + 0.05
    // When matched against an option grid containing 0.1
    val variants = ConditionScreenStats.parameterVariants(center = drifted, options = listOf(0.05, 0.1, 0.2))

    // Then the centre is still found and its neighbours returned (a bit-equality check would skip it)
    assertEquals(listOf(0.05, 0.2), variants)
  }
}
