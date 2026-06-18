package ma.hiresync.cv.service.ai;

/**
 * Common contract for all LLM providers.
 * The gateway tries each enabled provider in order until one succeeds.
 */
public interface AiProvider {

    /** Human-readable label used in logs ("Gemini 2.0 Flash", "Groq", …). */
    String name();

    /**
     * Returns false when the provider is intentionally disabled (no API key, feature flag off…).
     * A disabled provider is silently skipped; no HTTP call is made.
     */
    boolean isEnabled();

    /**
     * Make one LLM call and return the model's text response.
     *
     * @throws Exception on any failure — connection error, 4xx/5xx, unexpected response shape.
     *                   The gateway catches this and tries the next provider.
     */
    String call(String systemPrompt, String userPrompt, int maxTokens, double temperature) throws Exception;
}
