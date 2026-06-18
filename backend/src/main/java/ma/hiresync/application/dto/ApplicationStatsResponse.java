package ma.hiresync.application.dto;

public record ApplicationStatsResponse(
        long total,
        long pending,      // applied + in_review
        long interviews,
        long offers,
        long rejected
) {}
