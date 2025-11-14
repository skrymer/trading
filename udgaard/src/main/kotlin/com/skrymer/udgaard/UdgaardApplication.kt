package com.skrymer.udgaard

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.PropertySource


@SpringBootApplication
@PropertySource("classpath:secure.properties")
class UdgaardApplication

fun main(args: Array<String>) {
	runApplication<UdgaardApplication>(*args)
}
