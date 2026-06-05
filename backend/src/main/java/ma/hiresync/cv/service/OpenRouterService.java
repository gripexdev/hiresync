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
                    "You are an expert ATS CV optimizer. You respond ONLY with a single valid JSON object, no markdown, no commentary."),
                Map.of("role", "user", "content", prompt)
            ),
            "max_tokens", 2800,
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
            You are optimizing a CV for a specific job. Return ONE JSON object with TWO keys: "optimizedCv" and "suggestions".

            ## ORIGINAL CV TEXT:
            %s

            ## TARGET JOB DESCRIPTION:
            %s

            ## OUTPUT FORMAT (return ONLY this JSON object, no markdown fences):
            {
              "optimizedCv": {
                "fullName": "candidate full name extracted from the CV",
                "jobTitle": "a professional title tailored to the target job (in French)",
                "contact": { "email": "", "phone": "", "location": "", "linkedin": "" },
                "summary": "a 2-3 sentence professional summary rewritten to target the job (French)",
                "experience": [
                  { "role": "", "company": "", "dates": "", "bullets": ["achievement with action verb and metrics", "..."] }
                ],
                "education": [ { "degree": "", "school": "", "dates": "" } ],
                "skills": ["skill1", "skill2"],
                "languages": ["Francais", "Anglais"]
              },
              "suggestions": [
                { "type": "keyword_added|section_rewritten|format_improved|skill_added",
                  "description": "what was improved (French, concise)",
                  "before": "original text (optional)",
                  "after": "improved text (optional)" }
              ]
            }

            ## RULES:
            - Rewrite content to maximize ATS score for the target job: inject missing keywords from the job description into skills and experience bullets, use strong action verbs, quantify achievements.
            - Keep all information truthful — do NOT invent jobs or degrees that aren't in the original.
            - All rewritten text in French. Maximum 6 suggestions. Maximum 4 experience entries.
            - Extract real contact info from the CV text if present.
            """.formatted(
                cvText.substring(0, Math.min(cvText.length(), 3000)),
                jobDescription.substring(0, Math.min(jobDescription.length(), 1200))
            );
    }

    /** Fallback when no API key is configured (development mode) */
    private String mockOptimization() {
        return """
            {
              "optimizedCv": {
                "fullName": "Othmane Sadiky",
                "jobTitle": "Ingenieur DevOps / Full Stack",
                "contact": { "email": "othmane@hiresync.ma", "phone": "+212 600 000 000", "location": "Casablanca", "linkedin": "" },
                "summary": "Ingenieur Full Stack avec 3 ans d'experience, specialise en automatisation CI/CD et orchestration Kubernetes.",
                "experience": [
                  { "role": "Developpeur Full Stack", "company": "Maroc Telecom", "dates": "2023-2026",
                    "bullets": ["Developpe des applications web pour 3M+ abonnes avec Angular et Spring Boot", "Mise en place de pipelines CI/CD reduisant le temps de deploiement de 40%"] }
                ],
                "education": [ { "degree": "Cycle Ingenieur GSI", "school": "ENSA Agadir", "dates": "2021-2026" } ],
                "skills": ["Angular", "Spring Boot", "Docker", "Kubernetes", "Terraform", "CI/CD"],
                "languages": ["Francais", "Anglais", "Arabe"]
              },
              "suggestions": [
                {"type":"keyword_added","description":"Ajout des mots-cles manquants identifies dans l'offre","after":"Kubernetes, Terraform, CI/CD"},
                {"type":"section_rewritten","description":"Reformulation du resume pour cibler le poste","before":"Developpeur experimente.","after":"Ingenieur DevOps avec expertise en automatisation CI/CD."},
                {"type":"format_improved","description":"Reorganisation des sections selon les standards ATS"},
                {"type":"skill_added","description":"Ajout des competences techniques manquantes"}
              ]
            }
            """;
    }
}
