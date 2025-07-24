package com.skrymer.udgaard.service

import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.repository.MarketBreadthRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class MarketBreadthService(
  val ovtlyrClient: OvtlyrClient,
  val marketBreadthRepository: MarketBreadthRepository
) {

  fun getMarketBreadth(marketSymbol: MarketSymbol = MarketSymbol.FULLSTOCK, fromDate: LocalDate, toDate: LocalDate, refresh: Boolean = false): MarketBreadth? {
    val marketBreadth = if (refresh) {
      fetchMarketBreadth(marketSymbol)
    }
    else {
      marketBreadthRepository.findById(marketSymbol)
        .orElseGet { fetchMarketBreadth() }
    }

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
      marketBreadthRepository.save(ovtlyrMarketBreadth.toModel())
    }
  }
}