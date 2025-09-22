package com.example.test.webhook;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private final RestTemplate restTemplate;

    public WebhookService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void executeFlow(String name, String regNo, String email,
                            String generateWebhookUrl, String fallbackSubmitUrl, String finalQuery) {
        try {
            Map<String, Object> respBody = generateWebhook(name, regNo, email, generateWebhookUrl);
            if (respBody == null) {
                log.error("generateWebhook returned null body");
                return;
            }

            String webhookUrl = findFirstString(respBody, List.of("webhook", "webhookUrl", "webhook_url"));
            String token = findFirstString(respBody, List.of("accessToken", "token"));

            if (webhookUrl == null) {
                log.warn("Webhook URL missing in response, using fallback {}", fallbackSubmitUrl);
                webhookUrl = fallbackSubmitUrl;
            }

            if (token == null || token.isBlank()) {
                log.error("Access token missing or blank in response: {}", respBody);
                return;
            }

            if (finalQuery == null || finalQuery.isBlank()) {
                log.error("No SQL query provided. Please configure app.finalQuery or final-query.sql");
                return;
            }

            submitFinalQuery(webhookUrl, token, finalQuery);

        } catch (Exception ex) {
            log.error("Exception in executeFlow", ex);
        }
    }

    private Map<String, Object> generateWebhook(String name, String regNo, String email, String url) {
        try {
            Map<String, String> requestBody = Map.of("name", name, "regNo", regNo, "email", email);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map<String, Object>> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity,
                            new ParameterizedTypeReference<Map<String, Object>>() {});

            log.info("generateWebhook response status: {}", response.getStatusCode());
            log.info("generateWebhook response body: {}", response.getBody());
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("HTTP error during generateWebhook: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Error during generateWebhook", e);
        }
        return null;
    }

    private void submitFinalQuery(String webhookUrl, String token, String finalQuery) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token); // default to Bearer

            Map<String, String> body = Map.of("finalQuery", finalQuery);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            log.info("Submitting final query to {} with token {}", webhookUrl, token);

            ResponseEntity<String> resp = restTemplate.postForEntity(webhookUrl, entity, String.class);
            log.info("Submit response code: {}", resp.getStatusCode());
            log.info("Submit response body: {}", resp.getBody());

        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("401 Unauthorized with Bearer token, trying x-api-key header...");
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("x-api-key", token); // fallback header

                Map<String, String> body = Map.of("finalQuery", finalQuery);
                HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

                ResponseEntity<String> resp = restTemplate.postForEntity(webhookUrl, entity, String.class);
                log.info("Submit response code (x-api-key): {}", resp.getStatusCode());
                log.info("Submit response body (x-api-key): {}", resp.getBody());
            } catch (Exception ex) {
                log.error("Failed submitting final query with x-api-key", ex);
            }
        } catch (Exception e) {
            log.error("Error submitting final query", e);
        }
    }

    private String findFirstString(Map<String, Object> map, List<String> keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v instanceof String s && !s.isEmpty()) {
                return s;
            }
        }
        return null;
    }
}
