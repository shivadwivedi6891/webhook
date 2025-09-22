package com.example.test;

import com.example.test.webhook.WebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ApiTestingApplication {

    @Value("${app.name}")
    private String name;

    @Value("${app.regNo}")
    private String regNo;

    @Value("${app.email}")
    private String email;

    @Value("${app.generateUrl}")
    private String generateUrl;

    @Value("${app.fallbackSubmitUrl}")
    private String fallbackSubmitUrl;

    @Value("${app.finalQuery:}")
    private String finalQuery;

    private final WebhookService webhookService;

    public ApiTestingApplication(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    public static void main(String[] args) {
        SpringApplication.run(ApiTestingApplication.class, args);
    }

    @Bean
    public CommandLineRunner runOnStartup() {
        return args -> {
            webhookService.executeFlow(name, regNo, email, generateUrl, fallbackSubmitUrl, finalQuery);
            System.exit(0); // optional: closes app after running once
        };
    }
}
