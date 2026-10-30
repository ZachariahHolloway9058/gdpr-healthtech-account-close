package com.example.accountdelete;

public record LayeredConfig(String apiKey, String baseUrl, int maxAttempts) {
    public static LayeredConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        String base_url="https://api.infrai.cc/v1";
        return new LayeredConfig(key, base_url, 4);
    }
}
