package com.temenos.coreservice;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    protected WebTestClient webTestClient;

    static {
        System.setProperty("DOCKER_HOST", "npipe:////./pipe/docker_engine");
        System.setProperty("testcontainers.docker.client.strategy", "org.testcontainers.dockerclient.NpipeSocketClientProviderStrategy");
        System.setProperty("testcontainers.checks.disable", "true");
    }

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("temenos_db")
            .withUsername("postgres")
            .withPassword("postgres");

    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    // Fix: Bind to 0.0.0.0 to allow connections from the Docker bridge
    static WireMockServer wireMockServer = new WireMockServer(
            WireMockConfiguration.wireMockConfig()
                    .port(8089)
                    .bindAddress("0.0.0.0")
    );

    @BeforeAll
    static void startContainers() {
        postgres.start();
        redis.start();

        // Fix: Expose host port 8089 to the containers
        org.testcontainers.Testcontainers.exposeHostPorts(8089);

        wireMockServer.start();
    }

    @AfterAll
    static void stopContainers() {
        wireMockServer.stop();
        redis.stop();
        postgres.stop();
    }

    @BeforeEach
    void setUpWebTestClient() {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        registry.add("spring.redisson.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/temenos_db");
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
    }
}