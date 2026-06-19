package ma.hiresync.cv.dto;

/**
 * Aggregate stats for the optimization history page — computed server-side so the
 * stat cards and filter-tab counts stay correct across pagination (they reflect
 * the whole dataset, not just the current page).
 */
public record HistoryStatsResponse(
        long total,
        long completed,
        long rejected,
        long failed,
        int  avgGain,
        int  bestScore
) {}
