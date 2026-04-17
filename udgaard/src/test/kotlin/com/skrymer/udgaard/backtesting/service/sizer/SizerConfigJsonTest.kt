package com.skrymer.udgaard.backtesting.service.sizer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class SizerConfigJsonTest {
  private val mapper: ObjectMapper = jacksonObjectMapper()

  @Test
  fun `deserialize atrRisk config`() {
    val json = """{"type":"atrRisk","riskPercentage":1.25,"nAtr":2.0}"""
    val config = mapper.readValue(json, SizerConfig::class.java)
    assertInstanceOf(AtrRiskSizerConfig::class.java, config)
    config as AtrRiskSizerConfig
    assertEquals(1.25, config.riskPercentage)
    assertEquals(2.0, config.nAtr)
  }

  @Test
  fun `deserialize percentEquity config`() {
    val json = """{"type":"percentEquity","percent":12.5}"""
    val config = mapper.readValue(json, SizerConfig::class.java)
    assertInstanceOf(PercentEquitySizerConfig::class.java, config)
    assertEquals(12.5, (config as PercentEquitySizerConfig).percent)
  }

  @Test
  fun `deserialize kelly config with default multiplier`() {
    val json = """{"type":"kelly","winRate":0.52,"winLossRatio":1.5}"""
    val config = mapper.readValue(json, SizerConfig::class.java) as KellySizerConfig
    assertEquals(0.52, config.winRate)
    assertEquals(1.5, config.winLossRatio)
    assertEquals(KellySizer.DEFAULT_FRACTION_MULTIPLIER, config.fractionMultiplier)
  }

  @Test
  fun `deserialize volTarget config`() {
    val json = """{"type":"volTarget","targetVolPct":0.5,"kAtr":1.0}"""
    val config = mapper.readValue(json, SizerConfig::class.java) as VolatilityTargetSizerConfig
    assertEquals(0.5, config.targetVolPct)
    assertEquals(1.0, config.kAtr)
  }

  @Test
  fun `round-trip atrRisk preserves fields`() {
    val original: SizerConfig = AtrRiskSizerConfig(1.25, 2.0)
    val roundtripped = mapper.readValue(mapper.writeValueAsString(original), SizerConfig::class.java)
    assertEquals(original, roundtripped)
  }

  @Test
  fun `toSizer produces correct concrete type`() {
    assertInstanceOf(AtrRiskSizer::class.java, AtrRiskSizerConfig(1.5, 2.0).toSizer())
    assertInstanceOf(PercentEquitySizer::class.java, PercentEquitySizerConfig(10.0).toSizer())
    assertInstanceOf(KellySizer::class.java, KellySizerConfig(0.52, 1.5).toSizer())
    assertInstanceOf(VolatilityTargetSizer::class.java, VolatilityTargetSizerConfig(0.5).toSizer())
  }
}
