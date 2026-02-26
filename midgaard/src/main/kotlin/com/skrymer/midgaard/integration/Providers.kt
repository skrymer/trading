package com.skrymer.midgaard.integration

import com.skrymer.midgaard.model.CompanyInfo
import com.skrymer.midgaard.model.Earning
import com.skrymer.midgaard.model.OptionContractDto
import com.skrymer.midgaard.model.RawBar
import java.time.LocalDate

interface OhlcvProvider {
    suspend fun getDailyBars(
        symbol: String,
        outputSize: String = "full",
        minDate: LocalDate = LocalDate.of(2000, 1, 1),
    ): List<RawBar>?
}

interface IndicatorProvider {
    suspend fun getATR(
        symbol: String,
        minDate: LocalDate = LocalDate.of(2000, 1, 1),
    ): Map<LocalDate, Double>?

    suspend fun getADX(
        symbol: String,
        minDate: LocalDate = LocalDate.of(2000, 1, 1),
    ): Map<LocalDate, Double>?
}

interface EarningsProvider {
    suspend fun getEarnings(symbol: String): List<Earning>?
}

interface CompanyInfoProvider {
    suspend fun getCompanyInfo(symbol: String): CompanyInfo?
}

interface OptionsProvider {
    fun getHistoricalOptions(
        symbol: String,
        date: String? = null,
    ): List<OptionContractDto>?

    fun findOptionContract(
        symbol: String,
        strike: Double,
        expiration: String,
        optionType: String,
        date: String,
    ): OptionContractDto?
}
