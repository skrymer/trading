package com.skrymer.udgaard.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class ClockConfig {
  // Pinned to US/Eastern: scan runs, trade entries, and signal_date all anchor to NYSE/NASDAQ
  // sessions. A diagnostic asking "what's today's scan?" must answer in the same zone the
  // scan was persisted in — otherwise a 22:00 Copenhagen run rolls into the next NY date.
  @Bean
  fun clock(): Clock = Clock.system(ZoneId.of("America/New_York"))
}
