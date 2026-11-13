package com.devdhar.taskmanager.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

/**
 * A provider that never answers must make the call fail after the configured
 * timeout instead of blocking the calling thread forever.
 */
class WebClientConfigTest {

    @Test
    void slowServerCausesTimeoutInsteadOfHanging() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(5_000); // longer than the 1s timeout below
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        try {
            WebClient client = new WebClientConfig().webClient(1);
            String url = "http://localhost:" + server.getAddress().getPort() + "/slow";

            assertTimeoutPreemptively(Duration.ofSeconds(4), () ->
                    assertThrows(RuntimeException.class,
                            () -> client.get().uri(url).retrieve().bodyToMono(String.class).block()));
        } finally {
            server.stop(0);
        }
    }
}
