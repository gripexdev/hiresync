package ma.hiresync.job.messaging;

import ma.hiresync.job.dto.EnrichTriggerResponse;
import ma.hiresync.job.service.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrichConsumerTest {

    @Mock private JobService jobService;
    @Mock private EnrichProducer enrichProducer;

    private EnrichConsumer consumer() {
        return new EnrichConsumer(jobService, enrichProducer);
    }

    @Test
    void consume_progressMadeAndJobsRemain_selfRequeues() {
        when(jobService.triggerEnrich()).thenReturn(new EnrichTriggerResponse(20, 100, 15));

        consumer().consume(new EnrichMessage("manual"));

        verify(enrichProducer).publish("self-requeue");
    }

    @Test
    void consume_noJobsRemain_doesNotRequeueEvenIfProgressWasMade() {
        when(jobService.triggerEnrich()).thenReturn(new EnrichTriggerResponse(5, 100, 0));

        consumer().consume(new EnrichMessage("manual"));

        verify(enrichProducer, never()).publish(any());
    }

    @Test
    void consume_noProgressMade_stopsEvenIfJobsRemain() {
        // Guards against an infinite loop when a source consistently fails enrichment.
        when(jobService.triggerEnrich()).thenReturn(new EnrichTriggerResponse(0, 100, 30));

        consumer().consume(new EnrichMessage("self-requeue"));

        verify(enrichProducer, never()).publish(any());
    }
}
