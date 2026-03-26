package com.temenos.coreservice;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;

class TimerIntegrationTest extends BaseIntegrationTest {

    @BeforeEach
    void setup() {
        wireMockServer.resetAll();
        // Tip: Added a body here to be safe against any empty-body parsing issues
        wireMockServer.stubFor(post(urlEqualTo("/callback"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));
    }

    @Test
    void shortTimer_shouldCompleteAndSendCallback() {
        String csrfToken = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();

        // CHANGE: Use 127.0.0.1.
        // Testcontainers tunnels the container's 127.0.0.1:8089 to your Windows 8089.
        String callbackUrl = "http://127.0.0.1:8089/callback";

        byte[] responseBody = webTestClient.post()
                .uri("/api/timers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "createdAt": %d,
                            "delay": 3,
                            "callbackUrl": "%s",
                            "csrfToken": "%s"
                        }
                        """.formatted(createdAt, callbackUrl, csrfToken))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.timerId").isNotEmpty()
                .jsonPath("$.status").isEqualTo("PENDING")
                .returnResult()
                .getResponseBody();

        String timerId = JsonPath.read(new String(responseBody), "$.timerId");

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                wireMockServer.verify(postRequestedFor(urlEqualTo("/callback"))
                        .withRequestBody(matchingJsonPath("$.timerId", equalTo(timerId)))
                        .withRequestBody(matchingJsonPath("$.csrfToken", equalTo(csrfToken))))
        );
    }

    @Test
    void longTimer_shouldCompleteAndSendCallback() {
        String csrfToken = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();

        // CHANGE: Use 127.0.0.1 here as well
        String callbackUrl = "http://127.0.0.1:8089/callback";

        byte[] responseBody = webTestClient.post()
                .uri("/api/timers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "createdAt": %d,
                            "delay": 10,
                            "callbackUrl": "%s",
                            "csrfToken": "%s"
                        }
                        """.formatted(createdAt, callbackUrl, csrfToken))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.timerId").isNotEmpty()
                .returnResult()
                .getResponseBody();

        String timerId = JsonPath.read(new String(responseBody), "$.timerId");

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                wireMockServer.verify(postRequestedFor(urlEqualTo("/callback"))
                        .withRequestBody(matchingJsonPath("$.timerId", equalTo(timerId)))
                        .withRequestBody(matchingJsonPath("$.csrfToken", equalTo(csrfToken))))
        );
    }
}