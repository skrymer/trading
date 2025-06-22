package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class HeatmapExitStrategy: ExitStrategy {

    /**
     * If entry heatmap value was less than 50, return true when value is above 63
     * If entry heatmap value was between 50 and 75, return true when value is 10pt higher than entry value.
     * If entry heatmap value was greater than 75, return true when value is 5pt higher than entry value.
     */
    override fun test(
        entryQuote: StockQuote?,
        quote: StockQuote
    ): Boolean {

      if(entryQuote == null){
        return true
      }

      return when {
        entryQuote.heatmap >= 50 && entryQuote.heatmap <= 75 -> quote.heatmap >= (entryQuote.heatmap + 10)
        entryQuote.heatmap > 75 -> quote.heatmap >= (entryQuote.heatmap + 5)
        else -> quote.heatmap >= 63
      }
    }

    override fun reason(entryQuote: StockQuote?, quote: StockQuote) = "Stock heatmap has reached max value. Heatmap at entry ${entryQuote?.heatmap} now ${quote.heatmap}"

    override fun description() = "Heatmap exit strategy"
}