package com.skrymer.midgaard.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.env.PropertiesPropertySource
import java.io.File
import java.io.FileInputStream
import java.util.Properties

class ExternalConfigLoader : ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    private val logger = LoggerFactory.getLogger(ExternalConfigLoader::class.java)

    override fun onApplicationEvent(event: ApplicationEnvironmentPreparedEvent) {
        val configDir = File(System.getProperty("user.home"), ".trading-app")
        val configFile = File(configDir, "config.properties")

        if (configFile.exists()) {
            try {
                val properties = Properties()
                FileInputStream(configFile).use { properties.load(it) }

                val nonEmptyProperties = Properties()
                properties.forEach { key, value ->
                    val valueStr = value.toString().trim()
                    if (valueStr.isNotEmpty()) {
                        nonEmptyProperties[key] = value
                    }
                }

                if (nonEmptyProperties.isNotEmpty()) {
                    val propertySource = PropertiesPropertySource("externalConfig", nonEmptyProperties)
                    event.environment.propertySources.addLast(propertySource)
                    logger.info("Loaded external configuration from: ${configFile.absolutePath}")
                }
            } catch (e: Exception) {
                logger.error("Failed to load external configuration from: ${configFile.absolutePath}", e)
            }
        } else {
            logger.info("External configuration file not found: ${configFile.absolutePath}")
            logger.info("Using secure.properties for local development")
        }
    }
}
