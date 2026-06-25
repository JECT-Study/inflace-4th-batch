package com.infalce.batch.domain.youtube.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YoutubeApiClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retriesWithFallbackApiKeyWhenQuotaExceeded() {
        List<String> requestedKeys = new ArrayList<>();
        YoutubeApiClient client = client(exchange -> {
            String key = queryParams(exchange).get("key");
            requestedKeys.add(key);
            if ("primary-key".equals(key)) {
                writeJson(exchange, 403, quotaExceededBody());
                return;
            }
            writeJson(exchange, 200, """
                    {
                      "items": [
                        {
                          "id": "22",
                          "snippet": {
                            "title": "People & Blogs",
                            "assignable": true
                          }
                        }
                      ]
                    }
                    """);
        });

        List<YoutubeApiClient.YoutubeCategoryItem> categories = client.listVideoCategories("KR", "ko_KR");

        assertEquals(List.of("primary-key", "fallback-key"), requestedKeys);
        assertEquals(1, categories.size());
        assertEquals(22, categories.get(0).youtubeCategoryId());
    }

    @Test
    void doesNotRetryWithFallbackApiKeyWhenForbiddenReasonIsNotQuotaExceeded() {
        List<String> requestedKeys = new ArrayList<>();
        YoutubeApiClient client = client(exchange -> {
            requestedKeys.add(queryParams(exchange).get("key"));
            writeJson(exchange, 403, """
                    {
                      "error": {
                        "code": 403,
                        "errors": [
                          {
                            "reason": "forbidden"
                          }
                        ]
                      }
                    }
                    """);
        });

        assertThrows(YoutubeApiException.class, () -> client.listVideoCategories("KR", "ko_KR"));
        assertEquals(List.of("primary-key"), requestedKeys);
    }

    private YoutubeApiClient client(ExchangeHandler handler) {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/videoCategories", exchange -> {
                try {
                    handler.handle(exchange);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            });
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        YoutubeBatchProperties properties = new YoutubeBatchProperties();
        properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setApiKey("primary-key");
        properties.setFallbackApiKeys(List.of("fallback-key"));
        return new YoutubeApiClient(properties, new ObjectMapper());
    }

    private Map<String, String> queryParams(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        return java.util.Arrays.stream(query.split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(
                        parameter -> decode(parameter[0]),
                        parameter -> parameter.length > 1 ? decode(parameter[1]) : ""
                ));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String quotaExceededBody() {
        return """
                {
                  "error": {
                    "code": 403,
                    "message": "The request cannot be completed because you have exceeded your quota.",
                    "errors": [
                      {
                        "message": "The request cannot be completed because you have exceeded your quota.",
                        "domain": "youtube.quota",
                        "reason": "quotaExceeded"
                      }
                    ]
                  }
                }
                """;
    }

    private interface ExchangeHandler {

        void handle(HttpExchange exchange) throws Exception;
    }
}
