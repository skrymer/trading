package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.dto.SymbolRecord
import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.repository.StockJooqRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SymbolServiceTest {
  private lateinit var stockRepository: StockJooqRepository
  private lateinit var service: SymbolService

  @BeforeEach
  fun setup() {
    stockRepository = mock()
    service = SymbolService(stockRepository)
  }

  @Test
  fun `getAll returns the symbols and asset types held in the stocks table`() {
    // Given the local universe (the ingested stocks) holds a stock and a leveraged ETF
    whenever(stockRepository.findAllSymbolRecords()).thenReturn(
      listOf(
        SymbolRecord("AAPL", AssetType.STOCK),
        SymbolRecord("LABU", AssetType.LEVERAGED_ETF),
      ),
    )

    // When the universe is requested
    val result = service.getAll()

    // Then it is sourced from the stocks table, carrying each symbol's asset type
    assertEquals(
      listOf(SymbolRecord("AAPL", AssetType.STOCK), SymbolRecord("LABU", AssetType.LEVERAGED_ETF)),
      result,
    )
  }

  @Test
  fun `search prefix-matches symbols held in the stocks table`() {
    // Given the local universe holds two AA-prefixed names and one unrelated name
    whenever(stockRepository.findAllSymbolRecords()).thenReturn(
      listOf(
        SymbolRecord("AAPL", AssetType.STOCK),
        SymbolRecord("AAL", AssetType.STOCK),
        SymbolRecord("MSFT", AssetType.STOCK),
      ),
    )

    // When searching for the "AA" prefix
    val result = service.search("AA")

    // Then only the prefix matches are returned
    assertEquals(listOf("AAPL", "AAL"), result)
  }
}
