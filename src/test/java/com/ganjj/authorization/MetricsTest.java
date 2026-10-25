package com.ganjj.authorization;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetricsTest {
    @LocalServerPort int port;

    @Test void prometheusIsAvailableWithoutToken() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var instance = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/instance")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(instance.statusCode()).isEqualTo(200);
            var metrics = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/metrics")).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(metrics.statusCode()).isEqualTo(200);
            assertThat(metrics.body()).contains("jvm_memory_used_bytes", "http_server_requests_seconds");
        }
    }
}
