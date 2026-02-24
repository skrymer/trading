package com.skrymer.udgaard.controller.dto

data class ApiCredentialsDto(
  val ovtlyrToken: String = "",
  val ovtlyrUserId: String = "",
  val alphaVantageApiKey: String = "",
  val massiveApiKey: String = "",
  val ibkrAccountId: String = "",
  val ibkrFlexQueryId: String = "",
)
