package ma.hiresync.cv.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Google Gemini 2.0 Flash — free tier: 15 RPM, 1 500 req/day.
 * Obtain a key at https://aistudio.google.com/apikey (no credit card required).
 *
 * Set env var: GEMINI_API_KEY=AIza...
 */
@Component
@Slf4j
public class GeminiProvider implements AiProvider {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String MODEL    = "gemini-2.0-flash";

    private final WebClient    client;
    private final String       apiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiProvider(@Value("${hiresync.gemini.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.client = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override public String  name()      { return "Gemini 2.0 Flash"; }
    @Override public boolean isEnabled() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public String call(String systemPrompt, String userPrompt, int maxTokens, double temperature)
            throws Exception {
        var body = Map.of(
            "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
            "contents",          List.of(Map.of("parts", List.of(Map.of("text", userPrompt)))),
            "generationConfig",  Map.of("maxOutputTokens", maxTokens, "temperature", temperature)
        );

        String raw;
        try {
            raw = client.post()
                    .uri("/v1beta/models/{model}:generateContent?key={key}", MODEL, apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();
        } catch (WebClientResponseException e) {
            int code = e.getStatusCode().value();
            String snippet = e.getResponseBodyAsString();
            if (snippet.length() > 200) snippet = snippet.substring(0, 200) + "…";
            if (code == 429) throw new RuntimeException("Gemini rate limited (429)");
            if (code == 401 || code == 403) throw new RuntimeException("Gemini auth error (" + code + "): invalid API key");
            throw new RuntimeException("Gemini HTTP " + code + ": " + snippet);
        } catch (WebClientRequestException e) {
            throw new RuntimeException("Gemini connection failed: " + e.getMostSpecificCause().getMessage());
        }

        if (raw == null || raw.isBlank()) throw new RuntimeException("Gemini: empty response");

        JsonNode root = mapper.readTree(raw);

        // Check for API-level error object (sometimes returned as 200 with error body)
        if (root.has("error")) {
            String msg = root.path("error").path("message").asText("unknown Gemini error");
            throw new RuntimeException("Gemini API error: " + msg);
        }

        JsonNode candidate = root.path("candidates").get(0);
        if (candidate == null) throw new RuntimeException("Gemini: no candidates in response");

        // Handle safety blocks and other finish reasons that produce no content
        String finishReason = candidate.path("finishReason").asText("");
        if ("SAFETY".equals(finishReason) || "RECITATION".equals(finishReason)) {
            throw new RuntimeException("Gemini blocked by safety filter (" + finishReason + ")");
        }

        JsonNode part = candidate.path("content").path("parts").get(0);
        if (part == null || part.isMissingNode()) throw new RuntimeException("Gemini: malformed response structure");

        String text = part.path("text").asText(null);
        if (text == null || text.isBlank()) throw new RuntimeException("Gemini: empty text in response");
        return text;
    }
}
