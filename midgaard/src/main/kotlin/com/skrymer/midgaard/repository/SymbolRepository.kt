package com.skrymer.midgaard.repository

import com.skrymer.midgaard.jooq.tables.references.SYMBOLS
import com.skrymer.midgaard.model.AssetType
import com.skrymer.midgaard.model.Symbol
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class SymbolRepository(
    private val dsl: DSLContext,
) {
    fun findAll(): List<Symbol> =
        dsl
            .selectFrom(SYMBOLS)
            .orderBy(SYMBOLS.SYMBOL.asc())
            .fetch()
            .map { record ->
                Symbol(
                    symbol = record.symbol,
                    assetType = AssetType.valueOf(record.assetType),
                    sector = record.sector,
                    sectorSymbol = record.sectorSymbol,
                )
            }

    fun findBySymbol(symbol: String): Symbol? =
        dsl
            .selectFrom(SYMBOLS)
            .where(SYMBOLS.SYMBOL.eq(symbol))
            .fetchOne()
            ?.let { record ->
                Symbol(
                    symbol = record.symbol,
                    assetType = AssetType.valueOf(record.assetType),
                    sector = record.sector,
                    sectorSymbol = record.sectorSymbol,
                )
            }

    fun upsertSymbol(symbol: Symbol) {
        dsl
            .insertInto(SYMBOLS)
            .set(SYMBOLS.SYMBOL, symbol.symbol)
            .set(SYMBOLS.ASSET_TYPE, symbol.assetType.name)
            .set(SYMBOLS.SECTOR, symbol.sector)
            .set(SYMBOLS.SECTOR_SYMBOL, symbol.sectorSymbol)
            .onConflict(SYMBOLS.SYMBOL)
            .doUpdate()
            .set(SYMBOLS.ASSET_TYPE, symbol.assetType.name)
            .set(SYMBOLS.SECTOR, symbol.sector)
            .set(SYMBOLS.SECTOR_SYMBOL, symbol.sectorSymbol)
            .execute()
    }

    fun count(): Int = dsl.fetchCount(SYMBOLS)
}
