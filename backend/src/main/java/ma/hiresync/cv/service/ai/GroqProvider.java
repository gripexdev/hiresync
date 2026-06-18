package ma.hiresync.cv.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Groq — free community tier: ~14 400 req/day on Llama 3.3 70B.
 * Groq's hardware makes this the fastest cloud fallback (often < 2s).
 * Obtain a key at https://console.groq.com/keys (no credit card required).
 *
 * Set env var: GROQ_API_KEY=gsk_...
 */
@Component
@Slf4j
public class GroqProvider implements AiProvider {

    private static final String BASE_URL = "https://api.groq.com/openai/v1";
    private static final String MODEL    = "llama-3.3-70b-versatile";

    private final WebClient client;
    private final String    apiKey;

    public GroqProvider(@Value("${hiresync.groq.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        // Build with a placeholder header — the real key is injected per-request when enabled
        this.client = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override public String  name()      { return "Groq Llama 3.3 70B"; }
    @Override public boolean isEnabled() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public String call(String systemPrompt, String userPrompt, int maxTokens, double temperature)
            throws Exception {
        var body = Map.of(
            "model",    MODEL,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userPrompt)
            ),
            "max_tokens",  maxTokens,
            "temperature", temperature
        );

        Map<?, ?> response;
        try {
            response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (WebClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 429) throw new RuntimeException("Groq rate limited (429) — daily quota exhausted");
            if (code == 401) throw new RuntimeException("Groq auth error: invalid API key");
            String snippet = e.getResponseBodyAsString();
            if (snippet.length() > 200) snippet = snippet.substring(0, 200) + "…";
            throw new RuntimeException("Groq HTTP " + code + ": " + snippet);
        } catch (WebClientRequestException e) {
            throw new RuntimeException("Groq connection failed: " + e.getMostSpecificCause().getMessage());
        }

        if (response == null) throw new RuntimeException("Groq: empty response body");

        @SuppressWarnings("unchecked")
        var choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("Groq: no choices in response");

        @SuppressWarnings("unchecked")
        var message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) throw new RuntimeException("Groq: null message in first choice");

        String content = (String) message.get("content");
        if (content == null || content.isBlank()) throw new RuntimeException("Groq: empty content in response");
        return content;
    }
}
