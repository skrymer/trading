import type { ChartMarker, StockConditionSignals, StockExitConditionSignals } from '~/types'

// Direct ports of the in-chart logic that previously lived as three parallel forEach blocks
// inside StockPriceChart.client.vue's `updateMarkers()`. See
// docs/architecture/chart-marker-deepening.md for the why.

const STRATEGY_ENTRY_COLOR = '#2563eb'
const STRATEGY_EXIT_COLOR = '#f97316'
const ENTRY_CONDITION_COLOR = '#22c55e'
const EXIT_CONDITION_COLOR = '#ef4444'

function toLightweightChartsTime(isoDate: string): number {
  return (new Date(isoDate).getTime() / 1000) as unknown as number
}

// `signals` is the strategy-evaluation payload returned by GET /api/stocks/{symbol}/signals
// (StockWithSignals). Typed as `any` here because the page-level state is `Ref<any>` today —
// tightening that type is a separate cleanup tracked in the deepening doc.
//
// Entry markers collapse consecutive entrySignal=true bars into a single marker at the first
// bar of each run, with the run duration surfaced in `detail.sustainDays`. The backend now
// reports per-bar truth (see StrategySignalService.evaluateQuotes); without this collapse,
// a 5-day sustained breakout would render 5 stacked arrows obscuring the bar.
// See docs/adr/0004 and the grilling trail under Q8.
export function strategySignalsToMarkers(signals: any): ChartMarker[] {
  if (!signals?.quotesWithSignals) return []
  const markers: ChartMarker[] = []
  const qwsList = signals.quotesWithSignals

  let i = 0
  while (i < qwsList.length) {
    const qws = qwsList[i]
    const quote = qws.quote
    if (!quote?.date) {
      i++
      continue
    }

    if (qws.entrySignal) {
      // Find the end of this sustained run — the index of the last consecutive
      // entrySignal=true bar (the loop walks forward while the next bar also has
      // entrySignal AND a quote, so endQuote.date is safe to dereference below).
      let runEnd = i
      while (
        runEnd + 1 < qwsList.length
        && qwsList[runEnd + 1]?.entrySignal
        && qwsList[runEnd + 1]?.quote?.date
      ) runEnd++
      const runLength = runEnd - i + 1
      const endQuote = qwsList[runEnd].quote
      markers.push({
        time: toLightweightChartsTime(quote.date),
        position: 'belowBar',
        color: STRATEGY_ENTRY_COLOR,
        shape: 'arrowUp',
        text: runLength > 1 ? `Entry · ${runLength}d` : 'Entry',
        detail: {
          date: quote.date,
          price: quote.closePrice,
          entryDetails: qws.entryDetails,
          ...(runLength > 1 && { sustainDays: runLength, sustainEndDate: endQuote.date })
        }
      })
      // Exits can fire on any bar of the sustain (including the last). Emit a per-bar
      // exit marker for every bar in the run that triggered one — collapsing entries
      // does NOT collapse exits.
      for (let j = i; j <= runEnd; j++) {
        const runBar = qwsList[j]
        if (runBar?.exitSignal && runBar.quote?.date) {
          markers.push({
            time: toLightweightChartsTime(runBar.quote.date),
            position: 'aboveBar',
            color: STRATEGY_EXIT_COLOR,
            shape: 'arrowDown',
            text: runBar.exitReason || 'Exit'
          })
        }
      }
      i = runEnd + 1
      continue
    }

    // Non-entry bar: handle exit independently.
    if (qws.exitSignal) {
      markers.push({
        time: toLightweightChartsTime(quote.date),
        position: 'aboveBar',
        color: STRATEGY_EXIT_COLOR,
        shape: 'arrowDown',
        text: qws.exitReason || 'Exit'
      })
    }

    i++
  }
  return markers
}

export function conditionSignalsToMarkers(signals: StockConditionSignals | null | undefined): ChartMarker[] {
  if (!signals?.quotesWithConditions) return []
  const description = signals.conditionDescriptions.join(` ${signals.operator} `)
  return signals.quotesWithConditions.map((qwc) => {
    const passed = qwc.conditionResults.filter(r => r.passed).length
    const total = qwc.conditionResults.length
    return {
      time: toLightweightChartsTime(qwc.date),
      position: 'belowBar' as const,
      color: ENTRY_CONDITION_COLOR,
      shape: 'circle' as const,
      text: `${passed}/${total}`,
      detail: {
        date: qwc.date,
        price: qwc.closePrice,
        entryDetails: {
          strategyName: 'Condition Evaluation',
          strategyDescription: description,
          conditions: qwc.conditionResults,
          allConditionsMet: qwc.allConditionsMet
        }
      }
    }
  })
}

export function exitConditionSignalsToMarkers(signals: StockExitConditionSignals | null | undefined): ChartMarker[] {
  if (!signals?.quotesWithConditions) return []
  const description = `From entry ${signals.entryDate} — `
    + signals.conditionDescriptions.join(` ${signals.operator} `)
  return signals.quotesWithConditions.map((qwc) => {
    const fired = qwc.conditionResults.filter(r => r.passed).length
    const total = qwc.conditionResults.length
    return {
      time: toLightweightChartsTime(qwc.date),
      position: 'aboveBar' as const,
      color: EXIT_CONDITION_COLOR,
      shape: 'arrowDown' as const,
      text: `${fired}/${total}`,
      detail: {
        date: qwc.date,
        price: qwc.closePrice,
        entryDetails: {
          strategyName: 'Exit Condition Evaluation',
          strategyDescription: description,
          conditions: qwc.conditionResults,
          allConditionsMet: qwc.allConditionsMet
        }
      }
    }
  })
}
