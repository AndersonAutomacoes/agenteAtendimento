package com.atendimento.cerebro.testsupport;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

public final class AnalyticsIntegrationTestSupport {

    private AnalyticsIntegrationTestSupport() {}

    public static HttpHeaders bearerHeaders(String tenantId) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(IntegrationTestFirebaseSupport.bearerTokenForTenant(tenantId));
        return h;
    }

    public static <T> ResponseEntity<T> get(
            TestRestTemplate restTemplate, String url, String tenantId, Class<T> responseType) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(bearerHeaders(tenantId)), responseType);
    }
}
