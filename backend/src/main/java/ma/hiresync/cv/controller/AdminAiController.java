package ma.hiresync.cv.controller;

import lombok.RequiredArgsConstructor;
import ma.hiresync.cv.service.AiGatewayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dev/admin endpoints for probing the AI gateway.
 * GET  /api/admin/ai/providers  — list every provider and whether it's enabled
 * POST /api/admin/ai/test       — fire a test prompt through the gateway
 *
 * The test endpoint accepts a "skip" list so you can force individual providers
 * to be bypassed without restarting the server — useful for verifying the full
 * fallback chain in one session.
 */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final AiGatewayService aiGateway;

    /** List all providers and their enabled status. */
    @GetMapping("/providers")
    public ResponseEntity<List<AiGatewayService.ProviderStatus>> providers() {
        return ResponseEntity.ok(aiGateway.providerStatuses());
    }

    /**
     * Fire a test prompt through the gateway.
     *
     * Request body (all fields optional):
     * {
     *   "prompt":    "Bonjour, qui es-tu ?",           // defaults to a simple hello
     *   "skip":      ["Gemini 2.0 Flash", "Groq ..."]  // force these providers to be skipped
     * }
     *
     * Response:
     * {
     *   "provider": "Groq Llama 3.3 70B",
     *   "response": "Je suis un assistant IA...",
     *   "timeMs":   843,
     *   "errors":   ["Gemini 2.0 Flash: skipped (forced)"]
     * }
     */
    @PostMapping("/test")
    public ResponseEntity<AiGatewayService.AiTestResult> test(
            @RequestBody(required = false) Map<String, Object> body
    ) {
        String prompt = "Bonjour ! Réponds en une seule phrase : quel modèle d'IA es-tu ?";
        Set<String> skip = Set.of();

        if (body != null) {
            if (body.get("prompt") instanceof String p) prompt = p;
            if (body.get("skip") instanceof List<?> list) {
                skip = Set.copyOf(list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList());
            }
        }

        AiGatewayService.AiTestResult result = aiGateway.testCall(prompt, skip);
        return ResponseEntity.ok(result);
    }
}
