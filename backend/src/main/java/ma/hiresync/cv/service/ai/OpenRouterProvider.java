package ma.hiresync.cv.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * OpenRouter — free models (`:free` suffix).
 * Tries the primary model first, then the fallback chain.
 * On 429, backs off and retries the full chain rather than cycling models
 * (all `:free` models share the same per-key rate limit on OpenRouter).
 *
 * Set env var: OPENROUTER_API_KEY=sk-or-...
 */
@Component
@Slf4j
public class OpenRouterProvider implements AiProvider {

    private static final int  BACKOFF_RETRIES  = 3;
    private static final long BACKOFF_BASE_MS  = 4_000L;

    private final WebClient    client;
    private final String       apiKey;
    private final String       primaryModel;
    private final List<String> fallbackModels;

    public OpenRouterProvider(
            @Value("${hiresync.openrouter.api-key:}")                                                          String apiKey,
            @Value("${hiresync.openrouter.base-url:https://openrouter.ai/api/v1}")                             String baseUrl,
            @Value("${hiresync.openrouter.model:google/gemma-4-31b-it:free}")                                  String primaryModel,
            @Value("${hiresync.openrouter.fallback-models-csv:meta-llama/llama-3.3-70b-instruct:free,qwen/qwen3-next-80b-a3b-instruct:free}") String fallbackModelsCsv
    ) {
        this.apiKey         = apiKey;
        this.primaryModel   = primaryModel;
        this.fallbackModels = Arrays.asList(fallbackModelsCsv.split(","));
        this.client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization",  "Bearer " + apiKey)
                .defaultHeader("HTTP-Referer",   "https://hiresync.ma")
                .defaultHeader("X-Title",        "HireSync CV Optimizer")
                .defaultHeader("Content-Type",   "application/json")
                .build();
    }

    @Override public String  name()      { return "OpenRouter"; }
    @Override public boolean isEnabled() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public String call(String systemPrompt, String userPrompt, int maxTokens, double temperature)
            throws Exception {
        List<String> models = new ArrayList<>();
        models.add(primaryModel);
        models.addAll(fallbackModels);

        for (int attempt = 0; attempt <= BACKOFF_RETRIES; attempt++) {
            boolean rateLimited = false;

            for (String model : models) {
                try {
                    log.info("OpenRouter → model: {}", model);
                    return callModel(model, systemPrompt, userPrompt, maxTokens, temperature);
                } catch (Exception e) {
                    if (isRateLimit(e)) {
                        rateLimited = true;
                        log.warn("OpenRouter 429 on model {} — backing off", model);
                        break;   // cycling models is futile; they share the same limit
                    }
                    log.warn("OpenRouter model {} failed: {}", model, e.getMessage());
                }
            }

            if (!rateLimited) break;

            if (attempt < BACKOFF_RETRIES) {
                long wait = BACKOFF_BASE_MS * (attempt + 1);
                log.warn("OpenRouter all rate-limited — waiting {}ms (retry {}/{})", wait, attempt + 1, BACKOFF_RETRIES);
                Thread.sleep(wait);
            }
        }

        throw new RuntimeException("All OpenRouter models failed or rate-limited");
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private String callModel(String model, String systemPrompt, String userPrompt,
                             int maxTokens, double temperature) {
        var body = Map.of(
            "model",       model,
            "messages",    List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userPrompt)
            ),
            "max_tokens",  maxTokens,
            "temperature", temperature
        );

        var response = client.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(30))
                .block();

        if (response == null) throw new RuntimeException("OpenRouter: empty response from " + model);

        @SuppressWarnings("unchecked")
        var choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("OpenRouter: no choices from " + model);

        @SuppressWarnings("unchecked")
        var message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) throw new RuntimeException("OpenRouter: null message from " + model);

        String content = (String) message.get("content");
        if (content == null || content.isBlank()) throw new RuntimeException("OpenRouter: empty content from " + model + " (model may be overloaded)");
        return content;
    }

    private boolean isRateLimit(Throwable e) {
        if (e instanceof WebClientResponseException w) return w.getStatusCode().value() == 429;
        return e.getMessage() != null && e.getMessage().contains("429");
    }
}
