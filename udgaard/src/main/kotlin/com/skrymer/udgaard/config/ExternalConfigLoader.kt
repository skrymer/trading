package com.skrymer.udgaard.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileInputStream
import java.util.*

/**
 * Loads external configuration from ~/.trading-app/config.properties
 * This runs before Spring Boot initializes beans, so credentials are available to @Value annotations
 */
class ExternalConfigLoader : ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    private val logger = LoggerFactory.getLogger(ExternalConfigLoader::class.java)

    override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
        val configDir = File(System.getProperty("user.home"), ".trading-app")
        val configFile = File(configDir, "config.properties")

        if (configFile.exists()) {
            try {
                val properties = Properties()
                FileInputStream(configFile).use { properties.load(it) }

                // Filter out empty properties to allow secure.properties to take precedence
                val nonEmptyProperties = Properties()
                properties.forEach { key, value ->
                    val valueStr = value.toString().trim()
                    if (valueStr.isNotEmpty()) {
                        nonEmptyProperties[key] = value
                    }
                }

                // Only add if there are non-empty properties
                if (nonEmptyProperties.isNotEmpty()) {
                    val propertySource = PropertiesPropertySource("externalConfig", nonEmptyProperties)
                    event.environment.propertySources.addLast(propertySource)
                    logger.info("Loaded external configuration from: ${configFile.absolutePath}")
                    logger.info("Configuration properties loaded: ${nonEmptyProperties.keys.size} non-empty properties")
                } else {
                    logger.info("External configuration file exists but contains no non-empty properties: ${configFile.absolutePath}")
                    logger.info("Using secure.properties for local development or configure credentials via Settings page")
                }
            } catch (e: Exception) {
                logger.error("Failed to load external configuration from: ${configFile.absolutePath}", e)
            }
        } else {
            logger.info("External configuration file not found: ${configFile.absolutePath}")
            logger.info("Using secure.properties for local development or configure credentials via Settings page when app starts")
        }
    }
}
