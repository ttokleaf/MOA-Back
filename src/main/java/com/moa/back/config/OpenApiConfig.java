package com.moa.back.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("MOA Backend API")
				.description("Shared household finance app MOAs RESTful backend API")
				.version("0.0.1-SNAPSHOT")
				.contact(new Contact()
					.name("MOA Team")
					.email("contact@moa.com")))
			.servers(List.of(
				new Server().url("http://localhost:8080").description("Local Server"),
				new Server().url("https://api.moa.com").description("Production Server")
			));
	}
}

