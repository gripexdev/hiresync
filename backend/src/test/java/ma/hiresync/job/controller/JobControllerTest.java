package ma.hiresync.job.controller;

import ma.hiresync.job.dto.EnrichTriggerResponse;
import ma.hiresync.job.dto.JobResponse;
import ma.hiresync.job.dto.ScrapeTriggerResponse;
import ma.hiresync.job.filter.JobSearchCriteria;
import ma.hiresync.job.service.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock private JobService jobService;

    private JobController controller() {
        return new JobController(jobService);
    }

    @Test
    void search_buildsSearchCriteriaFromQueryParamsAndDelegates() {
        Page<JobResponse> page = new PageImpl<>(List.of());
        when(jobService.search(any(JobSearchCriteria.class), any(PageRequest.class))).thenReturn(page);

        var result = controller().search("java", "Casablanca", "CDI", "Confirmé", "IT", 0, 20);

        assertThat(result).isSameAs(page);
        verify(jobService).search(
                new JobSearchCriteria("java", "Casablanca", "CDI", "Confirmé", "IT"),
                PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("scrapedAt").descending()));
    }

    @Test
    void getById_found_returns200WithBody() {
        UUID id = UUID.randomUUID();
        var job = new JobResponse(id, "Dev", "Acme", "Casablanca", "CDI", "IT", "Confirmé",
                "desc", List.of(), null, null, "rekrute.com", null, null);
        when(jobService.getById(id)).thenReturn(Optional.of(job));

        var response = controller().getById(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(job);
    }

    @Test
    void getById_notFound_returns404() {
        UUID id = UUID.randomUUID();
        when(jobService.getById(id)).thenReturn(Optional.empty());

        var response = controller().getById(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void trigger_returns202AcceptedWithScrapeResponse() {
        var scrapeResponse = new ScrapeTriggerResponse(5, 0);
        when(jobService.triggerScrape()).thenReturn(scrapeResponse);

        var response = controller().trigger();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(scrapeResponse);
    }

    @Test
    void triggerEnrich_returns200WithEnrichResponse() {
        var enrichResponse = new EnrichTriggerResponse(20, 100, 15);
        when(jobService.triggerEnrich()).thenReturn(enrichResponse);

        var response = controller().triggerEnrich();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(enrichResponse);
    }
}
