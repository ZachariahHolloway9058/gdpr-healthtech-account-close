package com.example.accountdelete;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InfraiClient {
    private final LayeredConfig config;
    private final HttpClient http;

    public InfraiClient(LayeredConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String listSessions(String userId) throws IOException, InterruptedException {
        return request("GET", "/auth/session/list_for_user/" + encode(userId), "");
    }

    public String revokeSession(String sessionId) throws IOException, InterruptedException {
        String body = "{\"session_id\":\"" + json(sessionId) + "\"}";
        return request("POST", "/auth/session/revoke/" + encode(sessionId), body);
    }

    public String revokeKey(String keyId) throws IOException, InterruptedException {
        return request("DELETE", "/account/keys/revoke/" + encode(keyId), "");
    }

    private String request(String method, String path, String body) throws IOException, InterruptedException {
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            if (!body.isEmpty()) builder.header("Content-Type", "application/json");
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String payload = response.body();
            if (payload.contains("\"ok\":false")) {
                throw new InfraiException(errorCode(payload), payload, response.statusCode());
            }
            if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                Thread.sleep(retryDelay(response, attempt));
                continue;
            }
            if (response.statusCode() >= 500) throw new IOException("Infrai transport status " + response.statusCode());
            return payload;
        }
        throw new IOException("request attempts exhausted");
    }

    private long retryDelay(HttpResponse<String> response, int attempt) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse("");
        try { return Long.parseLong(retryAfter) * 1000L; }
        catch (NumberFormatException ignored) { return 200L * (1L << (attempt - 1)); }
    }

    private static String errorCode(String payload) {
        Matcher m = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)").matcher(payload);
        return m.find() ? m.group(1) : "INFRAI_REJECTED";
    }
    private static String encode(String value) { return value.replace("/", "%2F"); }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
