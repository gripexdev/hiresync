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
 * Ollama local model — completely free, no API key, no rate limits.
 * Requires Ollama to be running locally or in Docker:
 *   docker run -d -p 11434:11434 -v ollama:/root/.ollama ollama/ollama
 *   docker exec -it <container> ollama pull llama3.2
 *
 * Uses the OpenAI-compatible endpoint Ollama exposes at /v1/chat/completions.
 *
 * Disable entirely in prod if no GPU is available:
 *   hiresync.local-ai.enabled: false
 */
@Component
@Slf4j
public class LocalModelProvider implements AiProvider {

    private final WebClient client;
    private final String    model;
    private final boolean   enabled;

    public LocalModelProvider(
            @Value("${hiresync.local-ai.url:http://localhost:11434}")  String baseUrl,
            @Value("${hiresync.local-ai.model:llama3.2}")              String model,
            @Value("${hiresync.local-ai.enabled:false}")               boolean enabled
    ) {
        this.model   = model;
        this.enabled = enabled;
        this.client  = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override public String  name()      { return "Local Ollama (" + model + ")"; }
    @Override public boolean isEnabled() { return enabled; }

    @Override
    public String call(String systemPrompt, String userPrompt, int maxTokens, double temperature)
            throws Exception {
        var body = Map.of(
            "model",    model,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userPrompt)
            ),
            "max_tokens",  maxTokens,
            "temperature", temperature,
            "stream",      false
        );

        // Local models can be slow — allow up to 2 minutes
        Map<?, ?> response;
        try {
            response = client.post()
                    .uri("/v1/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(120))
                    .block();
        } catch (WebClientRequestException e) {
            // Connection refused means Ollama isn't running
            throw new RuntimeException("Local model (Ollama) not reachable — is it running? " + e.getMostSpecificCause().getMessage());
        } catch (WebClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 404) throw new RuntimeException("Local model '" + model + "' not found — run: ollama pull " + model);
            throw new RuntimeException("Local model HTTP " + code + ": " + e.getResponseBodyAsString().substring(0, Math.min(150, e.getResponseBodyAsString().length())));
        }

        if (response == null) throw new RuntimeException("Local model: empty response");

        @SuppressWarnings("unchecked")
        var choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("Local model: no choices in response");

        @SuppressWarnings("unchecked")
        var message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) throw new RuntimeException("Local model: null message in response");

        String content = (String) message.get("content");
        if (content == null || content.isBlank()) throw new RuntimeException("Local model: empty content in response");
        return content;
    }
}
