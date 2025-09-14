package com.skrymer.udgaard.service

import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.StockSymbol
import com.skrymer.udgaard.repository.MarketBreadthRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class MarketBreadthService(
  val ovtlyrClient: OvtlyrClient,
  val marketBreadthRepository: MarketBreadthRepository
) {

  fun getMarketBreadth(marketSymbol: MarketSymbol = MarketSymbol.FULLSTOCK, refresh: Boolean = false): MarketBreadth? {
    return if (refresh) {
      fetchMarketBreadth(marketSymbol)
    }
    else {
      marketBreadthRepository.findById(marketSymbol)
        .orElseGet { fetchMarketBreadth() }
    }
  }

  fun getMarketBreadth(marketSymbol: MarketSymbol = MarketSymbol.FULLSTOCK, fromDate: LocalDate, toDate: LocalDate, refresh: Boolean = false): MarketBreadth? {
    val marketBreadth = getMarketBreadth(marketSymbol, refresh)

    return MarketBreadth(
      marketBreadth?.symbol ?: marketSymbol,
      marketBreadth?.quotes
        ?.filter { it.quoteDate?.isAfter(fromDate) == true }
        ?.filter { it.quoteDate?.isBefore(toDate) == true }
        ?: emptyList()
    )
  }

  private fun fetchMarketBreadth(marketSymbol: MarketSymbol = MarketSymbol.FULLSTOCK): MarketBreadth? {
    val ovtlyrMarketBreadth = ovtlyrClient.getMarketBreadth(marketSymbol.name)

    return if (ovtlyrMarketBreadth == null) {
      return null
    }
    else {
      val stockInMarket = getStockInMarket(ovtlyrMarketBreadth.getMarketSymbol())
      marketBreadthRepository.save(ovtlyrMarketBreadth.toModel(stockInMarket))
    }
  }

  private fun getStockInMarket(marketSymbol: MarketSymbol): OvtlyrStockInformation? {
    return when(marketSymbol) {
      MarketSymbol.XLB -> ovtlyrClient.getStockInformation(StockSymbol.ALB.name)
      MarketSymbol.XLE -> ovtlyrClient.getStockInformation(StockSymbol.XOM.name)
      MarketSymbol.XLV -> ovtlyrClient.getStockInformation(StockSymbol.LLY.name)
      MarketSymbol.XLC -> ovtlyrClient.getStockInformation(StockSymbol.GOOGL.name)
      MarketSymbol.XLK -> ovtlyrClient.getStockInformation(StockSymbol.NVDA.name)
      MarketSymbol.XLRE -> ovtlyrClient.getStockInformation(StockSymbol.AMT.name)
      MarketSymbol.XLI -> ovtlyrClient.getStockInformation(StockSymbol.GE.name)
      MarketSymbol.XLF -> ovtlyrClient.getStockInformation(StockSymbol.JPM.name)
      MarketSymbol.XLY -> ovtlyrClient.getStockInformation(StockSymbol.AMZN.name)
      MarketSymbol.XLP -> ovtlyrClient.getStockInformation(StockSymbol.WMT.name)
      MarketSymbol.XLU -> ovtlyrClient.getStockInformation(StockSymbol.NEE.name)
      else -> null
    }
  }
}