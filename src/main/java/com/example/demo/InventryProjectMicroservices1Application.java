package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication
@EnableSpringDataWebSupport
public class InventryProjectMicroservices1Application {

	public static void main(String[] args) {
		SpringApplication.run(InventryProjectMicroservices1Application.class, args);
	}

}
