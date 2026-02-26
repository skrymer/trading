package com.skrymer.midgaard.model

import java.time.LocalDate

data class OptionContractDto(
    val contractId: String,
    val symbol: String,
    val strike: Double,
    val expiration: LocalDate,
    val optionType: String,
    val date: LocalDate,
    val price: Double,
    val impliedVolatility: Double? = null,
    val delta: Double? = null,
    val gamma: Double? = null,
    val theta: Double? = null,
    val vega: Double? = null,
)
