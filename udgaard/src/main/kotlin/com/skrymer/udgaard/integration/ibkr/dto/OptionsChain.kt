package com.skrymer.udgaard.integration.ibkr.dto

data class OptionsChain(
  val call: List<Double>,
  val put: List<Double>,
)
