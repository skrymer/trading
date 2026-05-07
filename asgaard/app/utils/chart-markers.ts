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
export function strategySignalsToMarkers(signals: any): ChartMarker[] {
  if (!signals?.quotesWithSignals) return []
  const markers: ChartMarker[] = []
  for (const qws of signals.quotesWithSignals) {
    const quote = qws.quote
    if (!quote?.date) continue
    const time = toLightweightChartsTime(quote.date)

    if (qws.entrySignal) {
      markers.push({
        time,
        position: 'belowBar',
        color: STRATEGY_ENTRY_COLOR,
        shape: 'arrowUp',
        text: 'Entry',
        detail: {
          date: quote.date,
          price: quote.closePrice,
          entryDetails: qws.entryDetails
        }
      })
    }

    if (qws.exitSignal) {
      markers.push({
        time,
        position: 'aboveBar',
        color: STRATEGY_EXIT_COLOR,
        shape: 'arrowDown',
        text: qws.exitReason || 'Exit'
      })
    }
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
