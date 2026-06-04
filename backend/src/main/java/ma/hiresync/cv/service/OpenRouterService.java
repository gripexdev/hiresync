package ma.hiresync.cv.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Calls OpenRouter API with Mistral 7B Instruct (free tier).
 *
 * Endpoint: POST https://openrouter.ai/api/v1/chat/completions
 * Fallback chain: Mistral 7B → LLaMA 3.1 8B → Gemma 2 9B
 *
 * Set env variable: OPENROUTER_API_KEY=sk-or-...
 */
@Service
@Slf4j
public class OpenRouterService {

    private final WebClient webClient;
    private final String apiKey;
    private final String primaryModel;
    private final List<String> fallbackModels;

    public OpenRouterService(
            @Value("${hiresync.openrouter.api-key}") String apiKey,
            @Value("${hiresync.openrouter.base-url}") String baseUrl,
            @Value("${hiresync.openrouter.model}") String primaryModel,
            // YAML lists don't bind with @Value; inject as comma-separated String and split
            @Value("${hiresync.openrouter.fallback-models-csv:meta-llama/llama-3.3-70b-instruct:free,qwen/qwen3-next-80b-a3b-instruct:free}") String fallbackModelsCsv
    ) {
        this.apiKey         = apiKey;
        this.primaryModel   = primaryModel;
        this.fallbackModels = java.util.Arrays.asList(fallbackModelsCsv.split(","));
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("HTTP-Referer", "https://hiresync.ma")
                .defaultHeader("X-Title", "HireSync CV Optimizer")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Optimize a CV for a specific job offer using Mistral 7B.
     *
     * @param cvText          extracted text from the original CV
     * @param jobDescription  job offer text (title + description + requirements)
     * @return JSON string with optimized suggestions
     */
    public String optimizeCv(String cvText, String jobDescription) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENROUTER_API_KEY not set — returning mock optimization");
            return mockOptimization();
        }

        String prompt = buildPrompt(cvText, jobDescription);

        // Try primary model, then fallbacks
        List<String> models = new java.util.ArrayList<>();
        models.add(primaryModel);
        models.addAll(fallbackModels);

        for (String model : models) {
            try {
                log.info("Calling OpenRouter model: {}", model);
                String result = callModel(model, prompt);
                log.info("OpenRouter responded successfully with model: {}", model);
                return result;
            } catch (Exception e) {
                log.warn("Model {} failed: {} — trying next fallback", model, e.getMessage());
            }
        }

        throw new RuntimeException("All OpenRouter models failed");
    }

    private String callModel(String model, String prompt) {
        var body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content",
                    "You are an expert ATS CV optimizer. Respond ONLY with a valid JSON array of suggested changes."),
                Map.of("role", "user", "content", prompt)
            ),
            "max_tokens", 1500,
            "temperature", 0.3
        );

        var response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(java.time.Duration.ofSeconds(30))
                .block();

        if (response == null) throw new RuntimeException("Empty response from OpenRouter");

        @SuppressWarnings("unchecked")
        var choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("No choices in response");

        @SuppressWarnings("unchecked")
        var message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    private String buildPrompt(String cvText, String jobDescription) {
        return """
            Analyze this CV against the job description and return a JSON array of improvement suggestions.

            ## CV TEXT:
            %s

            ## JOB DESCRIPTION:
            %s

            ## INSTRUCTIONS:
            Return ONLY a valid JSON array. Each element must have:
            - "type": one of "keyword_added", "section_rewritten", "format_improved", "skill_added"
            - "description": what change to make (in French, concise)
            - "before": original text (optional, only for rewrites)
            - "after": improved text (optional, only for rewrites)

            Focus on: missing ATS keywords, weak action verbs, missing quantified achievements.
            Return maximum 6 suggestions.
            """.formatted(
                cvText.substring(0, Math.min(cvText.length(), 2000)),
                jobDescription.substring(0, Math.min(jobDescription.length(), 1000))
            );
    }

    /** Fallback when no API key is configured (development mode) */
    private String mockOptimization() {
        return """
            [
              {"type":"keyword_added","description":"Ajout des mots-clés manquants identifiés dans l'offre","after":"Kubernetes, Terraform, CI/CD"},
              {"type":"section_rewritten","description":"Reformulation du résumé pour cibler le poste","before":"Développeur expérimenté.","after":"Ingénieur DevOps avec expertise en automatisation CI/CD et orchestration Kubernetes."},
              {"type":"format_improved","description":"Réorganisation des sections selon les standards ATS"},
              {"type":"skill_added","description":"Ajout des compétences techniques manquantes mentionnées dans l'offre"}
            ]
            """;
    }
}
