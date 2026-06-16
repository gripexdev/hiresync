package ma.hiresync.job.dto;

/** Result of POST /api/admin/scrape/trigger (now async — sources queued to RabbitMQ). */
public record ScrapeTriggerResponse(
        int  sourcesQueued,
        long totalJobsInDb
) {}
