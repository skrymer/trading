package com.skrymer.udgaard.controller.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiCredentialsDto(
  val ibkrAccountId: String = "",
  val ibkrFlexQueryId: String = "",
)
