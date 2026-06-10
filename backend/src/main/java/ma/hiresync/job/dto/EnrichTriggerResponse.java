package ma.hiresync.job.dto;

/** Result of POST /api/admin/enrich/trigger */
public record EnrichTriggerResponse(
        int  enrichedThisRun,
        long totalEnriched,
        long enrichedLeft
) {}
