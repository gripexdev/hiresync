package ma.hiresync.cv.controller;

import ma.hiresync.cv.service.AiGatewayService;
import ma.hiresync.cv.service.AiGatewayService.AiTestResult;
import ma.hiresync.cv.service.AiGatewayService.ProviderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAiControllerTest {

    @Mock private AiGatewayService aiGateway;

    private AdminAiController controller() {
        return new AdminAiController(aiGateway);
    }

    @Test
    void providers_delegatesDirectlyToGateway() {
        var statuses = List.of(new ProviderStatus("Gemini 2.0 Flash", true), new ProviderStatus("Local Ollama", false));
        when(aiGateway.providerStatuses()).thenReturn(statuses);

        var response = controller().providers();

        assertThat(response.getBody()).isEqualTo(statuses);
    }

    @Test
    void test_noBody_usesDefaultPromptAndEmptySkipSet() {
        var result = new AiTestResult("Groq Llama 3.3 70B", "Je suis...", 800, List.of());
        when(aiGateway.testCall(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Set.of())))
                .thenReturn(result);

        var response = controller().test(null);

        assertThat(response.getBody()).isEqualTo(result);
    }

    @Test
    void test_customPrompt_isPassedThrough() {
        var result = new AiTestResult("Gemini 2.0 Flash", "ok", 500, List.of());
        when(aiGateway.testCall("Custom prompt", Set.of())).thenReturn(result);

        controller().test(Map.of("prompt", "Custom prompt"));

        verify(aiGateway).testCall("Custom prompt", Set.of());
    }

    @Test
    void test_skipList_isConvertedToStringSet() {
        ArgumentCaptor<Set<String>> skipCaptor = ArgumentCaptor.forClass(Set.class);
        var result = new AiTestResult("OpenRouter", "ok", 500, List.of());
        when(aiGateway.testCall(org.mockito.ArgumentMatchers.anyString(), skipCaptor.capture())).thenReturn(result);

        controller().test(Map.of("skip", List.of("Gemini 2.0 Flash", "Groq Llama 3.3 70B")));

        assertThat(skipCaptor.getValue()).containsExactlyInAnyOrder("Gemini 2.0 Flash", "Groq Llama 3.3 70B");
    }

    @Test
    void test_skipListWithNonStringEntries_filtersThemOutSilently() {
        ArgumentCaptor<Set<String>> skipCaptor = ArgumentCaptor.forClass(Set.class);
        var result = new AiTestResult("OpenRouter", "ok", 500, List.of());
        when(aiGateway.testCall(org.mockito.ArgumentMatchers.anyString(), skipCaptor.capture())).thenReturn(result);

        controller().test(Map.of("skip", List.of("Gemini 2.0 Flash", 42)));

        assertThat(skipCaptor.getValue()).containsExactly("Gemini 2.0 Flash");
    }
}
