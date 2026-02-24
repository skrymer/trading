package com.skrymer.udgaard.data.repository

import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.jooq.tables.references.SYMBOLS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

data class SymbolRecord(
  val symbol: String,
  val assetType: AssetType,
  val sectorSymbol: String? = null,
)

@Repository
class SymbolJooqRepository(
  private val dsl: DSLContext,
) {
  fun findAll(): List<SymbolRecord> =
    dsl
      .selectFrom(SYMBOLS)
      .orderBy(SYMBOLS.SYMBOL.asc())
      .fetch { record ->
        SymbolRecord(
          symbol = record.get(SYMBOLS.SYMBOL)!!,
          assetType = AssetType.valueOf(record.get(SYMBOLS.ASSET_TYPE)!!),
          sectorSymbol = record.get(SYMBOLS.SECTOR_SYMBOL),
        )
      }

  fun findBySymbol(symbol: String): SymbolRecord? =
    dsl
      .selectFrom(SYMBOLS)
      .where(SYMBOLS.SYMBOL.eq(symbol))
      .fetchOne { record ->
        SymbolRecord(
          symbol = record.get(SYMBOLS.SYMBOL)!!,
          assetType = AssetType.valueOf(record.get(SYMBOLS.ASSET_TYPE)!!),
          sectorSymbol = record.get(SYMBOLS.SECTOR_SYMBOL),
        )
      }

  fun findByAssetType(assetType: AssetType): List<SymbolRecord> =
    dsl
      .selectFrom(SYMBOLS)
      .where(SYMBOLS.ASSET_TYPE.eq(assetType.name))
      .orderBy(SYMBOLS.SYMBOL.asc())
      .fetch { record ->
        SymbolRecord(
          symbol = record.get(SYMBOLS.SYMBOL)!!,
          assetType = AssetType.valueOf(record.get(SYMBOLS.ASSET_TYPE)!!),
          sectorSymbol = record.get(SYMBOLS.SECTOR_SYMBOL),
        )
      }

  fun findStocksWithoutSector(): List<SymbolRecord> =
    dsl
      .selectFrom(SYMBOLS)
      .where(SYMBOLS.ASSET_TYPE.eq(AssetType.STOCK.name))
      .and(SYMBOLS.SECTOR_SYMBOL.isNull)
      .orderBy(SYMBOLS.SYMBOL.asc())
      .fetch { record ->
        SymbolRecord(
          symbol = record.get(SYMBOLS.SYMBOL)!!,
          assetType = AssetType.valueOf(record.get(SYMBOLS.ASSET_TYPE)!!),
          sectorSymbol = record.get(SYMBOLS.SECTOR_SYMBOL),
        )
      }

  fun updateSectorSymbol(symbol: String, sectorSymbol: String) {
    dsl
      .update(SYMBOLS)
      .set(SYMBOLS.SECTOR_SYMBOL, sectorSymbol)
      .where(SYMBOLS.SYMBOL.eq(symbol))
      .execute()
  }
}
