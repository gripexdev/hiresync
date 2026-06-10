package ma.hiresync.job.dto;

/** Result of POST /api/admin/scrape/trigger */
public record ScrapeTriggerResponse(
        int  newJobsSaved,
        long totalJobsInDb
) {}
