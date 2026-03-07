package com.skrymer.midgaard.config

import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class VersionAdvice(private val buildProperties: BuildProperties) {
  @ModelAttribute("appVersion")
  fun appVersion(): String = buildProperties.version
}
