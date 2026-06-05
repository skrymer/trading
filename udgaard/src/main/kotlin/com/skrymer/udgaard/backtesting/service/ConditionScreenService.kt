package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.ConditionScreenReport
import com.skrymer.udgaard.backtesting.dto.ConditionScreenRequest
import com.skrymer.udgaard.backtesting.dto.FiringReport
import com.skrymer.udgaard.backtesting.dto.FiringYearReport
import com.skrymer.udgaard.backtesting.dto.GapReport
import com.skrymer.udgaard.backtesting.dto.HorizonReport
import com.skrymer.udgaard.backtesting.dto.ReferenceConditionSpec
import com.skrymer.udgaard.backtesting.dto.ReferenceOverlapReport
import com.skrymer.udgaard.backtesting.dto.RegimeBucketReport
import com.skrymer.udgaard.backtesting.dto.RegimeHorizonReport
import com.skrymer.udgaard.backtesting.dto.ScreenUniverse
import com.skrymer.udgaard.backtesting.dto.ScreenWindow
import com.skrymer.udgaard.backtesting.dto.SummaryReport
import com.skrymer.udgaard.backtesting.dto.SweepCell
import com.skrymer.udgaard.backtesting.dto.SweptParameter
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.SymbolService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Diagnostic, design-time pre-screen of an entry condition stack — `POST /api/conditions/screen`.
 *
 * Loads the stock universe and reference data once, evaluates the condition stack on every bar, and
 * delegates all arithmetic to [ConditionScreenStats]. Emits raw statistics and no verdict (ADR
 * 0007): the ARS / redundancy flag thresholds live in the analyst layer. The screen window is hard
 * capped at Block C's start so eyeballing its output cannot leak the firewall's only true OOS block.
 */
@Service
class ConditionScreenService(
  private val stockRepository: StockJooqRepository,
  private val symbolService: SymbolService,
  private val sectorBreadthRepository: SectorBreadthRepository,
  private val marketBreadthRepository: MarketBreadthRepository,
  private val conditionRegistry: ConditionRegistry,
) {
  private val logger = LoggerFactory.getLogger(ConditionScreenService::class.java)

  fun screen(request: ConditionScreenRequest): ConditionScreenReport {
    require(request.conditions.isNotEmpty()) { "At least one condition is required" }
    require(request.entryDelayDays >= 0) { "entryDelayDays must be >= 0, was ${request.entryDelayDays}" }
    require(request.horizons.isNotEmpty() && request.horizons.all { it > 0 }) {
      "horizons must be non-empty and strictly positive, was ${request.horizons}"
    }
    val start = request.startDate ?: DEFAULT_START
    val end = request.endDate ?: BLOCK_C_START
    require(!end.isAfter(BLOCK_C_START)) {
      "endDate $end is past 2021-01-01: Block C is the firewall's only true out-of-sample window and " +
        "screening on it at design time leaks it irrecoverably (ADR 0007). Move endDate to 2021-01-01 or earlier."
    }
    require(start.isBefore(end)) { "startDate $start must be before endDate $end" }

    val symbols = resolveSymbols(request)
    require(symbols.isNotEmpty()) { "No symbols resolved for the screen universe" }

    val context = buildContext(start)
    val notes = mutableListOf<String>()
    val prepared = prepareUniverse(symbols, start, end)

    // The all-bars baseline is invariant across the condition and every sweep cell — compute it once.
    val universe = buildUniverse(prepared, request.entryDelayDays, request.horizons)
    val universeCount = universe.size
    if (universe.isEmpty()) notes += "No eligible bars in the window — universe baseline is empty."

    // A templated script (one carrying `{{param}}` sweep placeholders) is not compilable as-is; the
    // base run substitutes each sweep's centre value to get the actual condition under test.
    val baseConfigs = materializeBaseConfigs(request)
    val (conditionSignals, conditionFirings) =
      conditionArm(prepared, baseConfigs, request.operator, context, request.entryDelayDays, request.horizons)

    return ConditionScreenReport(
      diagnosticNotice = DIAGNOSTIC_NOTICE,
      window = ScreenWindow(start, end, request.entryDelayDays),
      universe = ScreenUniverse(symbolCount = prepared.size, totalEligibleBars = universeCount),
      firing = firingReport(conditionSignals, universe, universeCount),
      forwardReturns = forwardReturnReports(conditionSignals, universe, request.horizons),
      signalToFillGap = gapReport(conditionSignals),
      spyRegime = spyRegimeReports(context, conditionSignals, universe, request.horizons),
      parameterSweep = computeSweep(request, prepared, universe, context, universeCount, notes),
      jaccard = computeJaccard(request.referenceConditions, conditionFirings, prepared, context, notes),
      notes = notes,
    )
  }

  /** Load, window-filter and sort each symbol's quotes once; every downstream pass reuses the result. */
  private fun prepareUniverse(
    symbols: List<String>,
    start: LocalDate,
    end: LocalDate,
  ): List<Pair<Stock, List<StockQuote>>> =
    stockRepository.findBySymbols(symbols, quotesAfter = start).map { stock ->
      stock to stock.quotes.filter { !it.date.isAfter(end) }.sortedBy { it.date }
    }

  /**
   * Evaluate a condition stack across the prepared universe, returning the fired signals (with their
   * forward returns) and the firing (symbol, date) set. The universe baseline is *not* recomputed
   * here — it is invariant across the condition and every sweep cell, so callers pass the shared one.
   */
  private fun conditionArm(
    prepared: List<Pair<Stock, List<StockQuote>>>,
    configs: List<ConditionConfig>,
    operator: String,
    context: BacktestContext,
    entryDelayDays: Int,
    horizons: List<Int>,
  ): Pair<List<SignalForwardReturn>, Set<SignalKey>> {
    val conditions = configs.map { conditionRegistry.buildEntryCondition(it) }
    val op = resolveOperator(operator)
    val signals = mutableListOf<SignalForwardReturn>()
    val firings = mutableSetOf<SignalKey>()
    for ((stock, quotes) in prepared) {
      for (i in quotes.indices) {
        if (matches(conditions, op, stock, quotes[i], context)) {
          signals += ConditionScreenStats.signalForwardReturn(quotes, i, entryDelayDays, horizons)
          firings += SignalKey(stock.symbol, quotes[i].date)
        }
      }
    }
    return signals to firings
  }

  /**
   * One-at-a-time parameter sweep. Registered conditions contribute every numeric tunable
   * (auto-enumerated from metadata); inline scripts contribute the declared `{{param}}` sweeps.
   * Capped at [MAX_SWEPT_PARAMS] tunables — overflow is noted, never silently dropped.
   */
  private fun computeSweep(
    request: ConditionScreenRequest,
    prepared: List<Pair<Stock, List<StockQuote>>>,
    universe: List<SignalForwardReturn>,
    context: BacktestContext,
    universeCount: Int,
    notes: MutableList<String>,
  ): List<SweptParameter> {
    val targets = sweepTargets(request, notes)
    val capped = targets.take(MAX_SWEPT_PARAMS)
    if (targets.size > capped.size) {
      notes += "Parameter sweep capped at $MAX_SWEPT_PARAMS tunables; ${targets.size - capped.size} not swept."
    }
    return capped.map { target ->
      val cells =
        target.variantValues.map { value ->
          val (cellSignals, _) =
            conditionArm(prepared, target.renderCell(value), request.operator, context, request.entryDelayDays, request.horizons)
          sweepCell(value, target.center, cellSignals, universe, universeCount, request.horizons)
        }
      SweptParameter(target.source, target.parameterName, target.center, cells)
    }
  }

  /** A tunable to sweep: its centre, the values to try, and how to render the cell's full config stack. */
  private data class SweepTarget(
    val source: String,
    val parameterName: String,
    val center: Double,
    val variantValues: List<Double>,
    val renderCell: (Double) -> List<ConditionConfig>,
  )

  private fun sweepTargets(
    request: ConditionScreenRequest,
    notes: MutableList<String>,
  ): List<SweepTarget> {
    val targets = mutableListOf<SweepTarget>()
    val centers = scriptCenters(request)
    val metadataByType = conditionRegistry.getEntryConditionMetadata().associateBy { it.type.lowercase() }

    request.conditions.forEachIndexed { index, config ->
      if (config.type.equals("script", ignoreCase = true)) return@forEachIndexed
      val metadata = metadataByType[config.type.lowercase()] ?: return@forEachIndexed
      metadata.parameters.filter { it.type == "number" }.forEach { param ->
        val center = (config.parameters[param.name] as? Number ?: param.defaultValue as? Number)?.toDouble() ?: return@forEach
        val options = param.options?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.isNotEmpty() }
        // An integer-typed tunable (Int/Long default) must sweep ±1 whole units, not ±10% — a relative
        // step truncates back onto the centre for small counts and gives a degenerate ARS cell.
        val isInteger = param.defaultValue is Int || param.defaultValue is Long
        val variants =
          ConditionScreenStats.parameterVariants(
            center,
            options,
            isInteger = isInteger,
            min = param.min?.toDouble(),
            max = param.max?.toDouble(),
          )
        if (variants.isEmpty()) {
          val reason =
            if (options != null) {
              "value is not on its allowed-option grid ${param.options}"
            } else {
              "no in-range neighbours within [${param.min}, ${param.max}]"
            }
          notes += "Tunable ${config.type}.${param.name}=$center not swept — $reason."
          return@forEach
        }
        targets +=
          SweepTarget(
            source = "condition:${config.type}",
            parameterName = param.name,
            center = center,
            variantValues = listOf(center) + variants,
            renderCell = { value -> renderConfigs(request, centers, registeredOverride = Triple(index, param.name, value)) },
          )
      }
    }

    request.scriptSweeps.forEach { spec ->
      if (request.conditions.none { it.type.equals("script", ignoreCase = true) }) return@forEach
      targets +=
        SweepTarget(
          source = "script:${spec.name}",
          parameterName = spec.name,
          center = spec.center,
          variantValues = listOf(spec.center - spec.step, spec.center, spec.center + spec.step),
          renderCell = { value -> renderConfigs(request, centers + (spec.name to value), registeredOverride = null) },
        )
    }
    return targets
  }

  private fun scriptCenters(request: ConditionScreenRequest): Map<String, Double> = request.scriptSweeps.associate { it.name to it.center }

  /** The actual condition stack under test: script placeholders substituted with their sweep centres. */
  private fun materializeBaseConfigs(request: ConditionScreenRequest): List<ConditionConfig> =
    renderConfigs(request, scriptCenters(request), registeredOverride = null)

  /**
   * Render the condition stack for one evaluation: substitute every `{{name}}` script placeholder
   * with [scriptValues] and, if present, override one registered numeric parameter.
   */
  private fun renderConfigs(
    request: ConditionScreenRequest,
    scriptValues: Map<String, Double>,
    registeredOverride: Triple<Int, String, Double>?,
  ): List<ConditionConfig> =
    request.conditions.mapIndexed { index, config ->
      var rendered = config
      if (registeredOverride != null && registeredOverride.first == index) {
        rendered = rendered.copy(parameters = rendered.parameters + (registeredOverride.second to registeredOverride.third))
      }
      if (rendered.type.equals("script", ignoreCase = true)) {
        var script = rendered.parameters["script"]?.toString() ?: ""
        scriptValues.forEach { (name, value) -> script = script.replace("{{$name}}", formatNumber(value)) }
        rendered = rendered.copy(parameters = rendered.parameters + ("script" to script))
      }
      rendered
    }

  private fun computeJaccard(
    references: List<ReferenceConditionSpec>,
    conditionFirings: Set<SignalKey>,
    prepared: List<Pair<Stock, List<StockQuote>>>,
    context: BacktestContext,
    notes: MutableList<String>,
  ): List<ReferenceOverlapReport> {
    if (references.isEmpty()) {
      notes += "No reference conditions supplied — symbol-date overlap (Jaccard) is N/A."
      return emptyList()
    }
    return references.map { ref ->
      // Only the firing set matters for overlap; reuse the condition arm and discard its forward returns.
      val (_, refFirings) = conditionArm(prepared, ref.conditions, ref.operator, context, entryDelayDays = 0, horizons = emptyList())
      val overlap = ConditionScreenStats.jaccardOverlap(conditionFirings, refFirings)
      ReferenceOverlapReport(ref.label, overlap.byYear, overlap.pooled)
    }
  }

  private fun resolveSymbols(request: ConditionScreenRequest): List<String> {
    request.symbols?.takeIf { it.isNotEmpty() }?.let { return it.map(String::uppercase) }
    val assetTypeEnums = request.assetTypes.map { AssetType.valueOf(it) }.toSet()
    return symbolService.getAll().filter { it.assetType in assetTypeEnums }.map { it.symbol }
  }

  private fun buildContext(start: LocalDate): BacktestContext {
    val spy = stockRepository.findBySymbol("SPY", quotesAfter = start)
    val spyQuoteMap = spy?.quotes?.associateBy { it.date } ?: emptyMap()
    logger.info("Condition screen context: ${spyQuoteMap.size} SPY quotes from $start")
    return BacktestContext(
      sectorBreadthMap = sectorBreadthRepository.findAllAsMap(),
      marketBreadthMap = marketBreadthRepository.findAllAsMap(),
      spyQuoteMap = spyQuoteMap,
    )
  }

  companion object {
    val DEFAULT_START: LocalDate = LocalDate.of(2000, 1, 1)
    val BLOCK_C_START: LocalDate = LocalDate.of(2021, 1, 1)
    const val MAX_SWEPT_PARAMS = 8
    private const val DIAGNOSTIC_NOTICE =
      "This is diagnostic, not predictive. A condition that passes /condition-screen is NOT validated. " +
        "A condition that fails /condition-screen is rejected without further work."
  }
}

private const val SPY_REGIME_LOOKBACK = 20
private const val CENTER_EPSILON = 1e-9

/** The all-bars forward-return baseline: one [SignalForwardReturn] per bar across the prepared universe. */
private fun buildUniverse(
  prepared: List<Pair<Stock, List<StockQuote>>>,
  entryDelayDays: Int,
  horizons: List<Int>,
): List<SignalForwardReturn> =
  prepared.flatMap { (_, quotes) ->
    quotes.indices.map { i -> ConditionScreenStats.signalForwardReturn(quotes, i, entryDelayDays, horizons) }
  }

private fun firingReport(
  conditionSignals: List<SignalForwardReturn>,
  universe: List<SignalForwardReturn>,
  universeCount: Int,
): FiringReport =
  FiringReport(
    totalSignals = conditionSignals.size,
    overallFiringRate = if (universeCount == 0) 0.0 else conditionSignals.size.toDouble() / universeCount,
    byYear =
      ConditionScreenStats.firingByYear(conditionSignals, universe).map {
        FiringYearReport(it.year, it.signals, it.eligibleBars, it.firingRate)
      },
  )

private fun forwardReturnReports(
  conditionSignals: List<SignalForwardReturn>,
  universe: List<SignalForwardReturn>,
  horizons: List<Int>,
): List<HorizonReport> =
  horizons.map { n ->
    val condition = ConditionScreenStats.summariseAt(conditionSignals, n)
    val universeSummary = ConditionScreenStats.summariseAt(universe, n)
    val lift = ConditionScreenStats.lift(condition, universeSummary)
    HorizonReport(n, condition.toReport(), universeSummary.toReport(), lift.meanLift, lift.hitRateLift)
  }

private fun spyRegimeReports(
  context: BacktestContext,
  conditionSignals: List<SignalForwardReturn>,
  universe: List<SignalForwardReturn>,
  horizons: List<Int>,
): List<RegimeHorizonReport> {
  val spy20d = spy20dReturns(context)
  return horizons.map { n ->
    val regimeBreakdown = ConditionScreenStats.spyRegimeBreakdown(spy20d, conditionSignals, universe, n)
    RegimeHorizonReport(n, regimeBreakdown.down.toReport(), regimeBreakdown.flat.toReport(), regimeBreakdown.up.toReport())
  }
}

/** Resolve the AND/OR operator once, outside the per-bar loop. Anything but "OR" (any case) is AND. */
private fun resolveOperator(operator: String): LogicalOperator =
  if (operator.uppercase() == "OR") LogicalOperator.OR else LogicalOperator.AND

private fun matches(
  conditions: List<EntryCondition>,
  operator: LogicalOperator,
  stock: Stock,
  quote: StockQuote,
  context: BacktestContext,
): Boolean =
  when (operator) {
    LogicalOperator.OR -> conditions.any { it.evaluate(stock, quote, context) }
    else -> conditions.all { it.evaluate(stock, quote, context) }
  }

private fun sweepCell(
  value: Double,
  center: Double,
  conditionSignals: List<SignalForwardReturn>,
  universeSignals: List<SignalForwardReturn>,
  universeCount: Int,
  horizons: List<Int>,
): SweepCell {
  val isCenter = kotlin.math.abs(value - center) < CENTER_EPSILON
  val liftByHorizon = mutableMapOf<Int, Double>()
  val seByHorizon = mutableMapOf<Int, Double>()
  for (n in horizons) {
    val conditionSummary = ConditionScreenStats.summariseAt(conditionSignals, n)
    val universeSummary = ConditionScreenStats.summariseAt(universeSignals, n)
    liftByHorizon[n] = ConditionScreenStats.lift(conditionSummary, universeSummary).meanLift
    seByHorizon[n] = conditionSummary.clustered.stdError
  }
  return SweepCell(
    parameterValue = value,
    isCenter = isCenter,
    relativeStep = if (center == 0.0) 0.0 else kotlin.math.abs(value - center) / kotlin.math.abs(center),
    firingRate = if (universeCount == 0) 0.0 else conditionSignals.size.toDouble() / universeCount,
    liftByHorizon = liftByHorizon,
    clusteredStdErrorByHorizon = seByHorizon,
  )
}

/** SPY 20-trading-day return per date: close[t] / close[t-20] - 1. */
private fun spy20dReturns(context: BacktestContext): Map<LocalDate, Double> {
  val spy = context.spyQuoteMap.values.sortedBy { it.date }
  val result = mutableMapOf<LocalDate, Double>()
  for (i in SPY_REGIME_LOOKBACK until spy.size) {
    val prior = spy[i - SPY_REGIME_LOOKBACK].closePrice
    if (prior > 0) result[spy[i].date] = spy[i].closePrice / prior - 1
  }
  return result
}

private fun gapReport(signals: List<SignalForwardReturn>): GapReport {
  val gaps = signals.mapNotNull { it.fillGap }
  if (gaps.isEmpty()) return GapReport(0.0, 0.0, 0.0, 0)
  val sorted = gaps.sorted()
  val mid = sorted.size / 2
  val median = if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
  return GapReport(
    meanGap = gaps.average(),
    medianGap = median,
    positiveRate = gaps.count { it > 0 }.toDouble() / gaps.size,
    n = gaps.size,
  )
}

private fun ForwardReturnSummary.toReport() =
  SummaryReport(
    signalCount = distribution.nSignals,
    dateCount = distribution.nDates,
    droppedCount = distribution.nDropped,
    mean = distribution.mean,
    median = distribution.median,
    std = distribution.std,
    skew = distribution.skew,
    hitRate = distribution.hitRate,
    clusteredMean = clustered.clusteredMean,
    clusteredStdError = clustered.stdError,
  )

private fun RegimeBucket.toReport() = RegimeBucketReport(firingRate, meanLift, nSignals)

private fun formatNumber(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
