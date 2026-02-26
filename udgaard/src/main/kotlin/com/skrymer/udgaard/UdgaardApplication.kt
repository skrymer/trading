package com.skrymer.udgaard

import com.skrymer.udgaard.config.SecurityProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.PropertySource
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory

@SpringBootApplication
@EnableConfigurationProperties(SecurityProperties::class)
@PropertySource("classpath:secure.properties", ignoreResourceNotFound = true)
class UdgaardApplication

fun main(args: Array<String>) {
  runApplication<UdgaardApplication>(*args)
}

@Component
class StartupLogger {
  private val logger = LoggerFactory.getLogger(StartupLogger::class.java)

  @EventListener(ApplicationReadyEvent::class)
  fun logMemoryConfig() {
    val memoryArgs = ManagementFactory
      .getRuntimeMXBean()
      .inputArguments
      .filter { it.startsWith("-X") }
    logger.info("JVM memory configuration: {}", memoryArgs.ifEmpty { listOf("(defaults)") })
  }
}
