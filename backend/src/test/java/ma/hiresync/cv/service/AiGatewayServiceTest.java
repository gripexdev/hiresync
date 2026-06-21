package ma.hiresync.cv.service;

import ma.hiresync.cv.service.ai.GeminiProvider;
import ma.hiresync.cv.service.ai.GroqProvider;
import ma.hiresync.cv.service.ai.LocalModelProvider;
import ma.hiresync.cv.service.ai.OpenRouterProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the provider fallback cascade — the single most important
 * resilience guarantee of the whole platform. We never hit a real LLM here:
 * each provider is a Mockito mock standing in for Gemini/Groq/OpenRouter/Local,
 * which is possible because AiGatewayService depends on the AiProvider
 * interface rather than on any HTTP client directly.
 */
@ExtendWith(MockitoExtension.class)
class AiGatewayServiceTest {

    @Mock private GeminiProvider gemini;
    @Mock private GroqProvider groq;
    @Mock private OpenRouterProvider openRouter;
    @Mock private LocalModelProvider local;

    private AiGatewayService gateway;

    @BeforeEach
    void setUp() {
        // Every provider needs a stable name() for logging/skip-list lookups,
        // even in tests where it's never asserted directly.
        lenient().when(gemini.name()).thenReturn("Gemini 2.0 Flash");
        lenient().when(groq.name()).thenReturn("Groq Llama 3.3 70B");
        lenient().when(openRouter.name()).thenReturn("OpenRouter");
        lenient().when(local.name()).thenReturn("Local Ollama");

        gateway = new AiGatewayService(gemini, groq, openRouter, local);
    }

    @Test
    void optimizeCv_firstEnabledProviderSucceeds_returnsItsResult() throws Exception {
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.call(anyString(), anyString(), anyInt(), anyDouble()))
                .thenReturn("{\"optimizedCv\": {}}");

        var result = gateway.optimizeCv("cv text", "job description");

        assertThat(result.provider()).isEqualTo("Gemini 2.0 Flash");
        assertThat(result.content()).isEqualTo("{\"optimizedCv\": {}}");
    }

    @Test
    void optimizeCv_firstProviderFails_fallsBackToNextEnabledProvider() throws Exception {
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.call(anyString(), anyString(), anyInt(), anyDouble()))
                .thenThrow(new RuntimeException("Gemini rate limited (429)"));
        when(groq.isEnabled()).thenReturn(true);
        when(groq.call(anyString(), anyString(), anyInt(), anyDouble()))
                .thenReturn("{\"optimizedCv\": {\"fullName\": \"ok\"}}");

        var result = gateway.optimizeCv("cv text", "job description");

        assertThat(result.provider()).isEqualTo("Groq Llama 3.3 70B");
    }

    @Test
    void optimizeCv_disabledProviderIsSkippedWithoutBeingCalled() throws Exception {
        when(gemini.isEnabled()).thenReturn(false); // no API key configured
        when(groq.isEnabled()).thenReturn(true);
        when(groq.call(anyString(), anyString(), anyInt(), anyDouble()))
                .thenReturn("{\"optimizedCv\": {}}");

        var result = gateway.optimizeCv("cv text", "job description");

        assertThat(result.provider()).isEqualTo("Groq Llama 3.3 70B");
        verify(gemini, never()).call(anyString(), anyString(), anyInt(), anyDouble());
    }

    @Test
    void optimizeCv_allProvidersFailOrDisabled_throwsWithAggregatedErrors() throws Exception {
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.call(anyString(), anyString(), anyInt(), anyDouble()))
                .thenThrow(new RuntimeException("Gemini auth error"));
        when(groq.isEnabled()).thenReturn(false);
        when(openRouter.isEnabled()).thenReturn(true);
        when(openRouter.call(anyString(), anyString(), anyInt(), anyDouble()))
                .thenThrow(new RuntimeException("OpenRouter rate limited"));
        when(local.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> gateway.optimizeCv("cv text", "job description"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("All AI providers failed")
                .hasMessageContaining("Gemini auth error")
                .hasMessageContaining("OpenRouter rate limited");
    }

    @Test
    void optimizeCv_explicitlySkippedProvider_isNeverCalledEvenIfEnabled() throws Exception {
        when(gemini.isEnabled()).thenReturn(true);
        when(groq.isEnabled()).thenReturn(true);
        when(groq.call(anyString(), anyString(), anyInt(), anyDouble()))
                .thenReturn("{\"optimizedCv\": {}}");

        var result = gateway.optimizeCv("cv text", "job description",
                java.util.Set.of("Gemini 2.0 Flash"));

        assertThat(result.provider()).isEqualTo("Groq Llama 3.3 70B");
        verify(gemini, never()).call(anyString(), anyString(), anyInt(), anyDouble());
    }

    @Test
    void assessCompatibility_allProvidersFail_failsOpenWithDefaultVerdict() throws Exception {
        when(gemini.isEnabled()).thenReturn(true);
        when(gemini.call(anyString(), anyString(), anyInt(), anyDouble()))
                .thenThrow(new RuntimeException("down"));
        when(groq.isEnabled()).thenReturn(false);
        when(openRouter.isEnabled()).thenReturn(false);
        when(local.isEnabled()).thenReturn(false);

        var verdict = gateway.assessCompatibility("cv text", "Backend Developer", "job description");

        // Fail-open: a candidate should never be wrongly rejected just because
        // every LLM provider happened to be down at that moment.
        assertThat(verdict.compatible()).isTrue();
        assertThat(verdict.score()).isEqualTo(60);
    }
}
