package com.skrymer.udgaard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "com.skrymer.udgaard.repository")
public class UdgaardApplication {

	public static void main(String[] args) {
		SpringApplication.run(UdgaardApplication.class, args);
	}
}