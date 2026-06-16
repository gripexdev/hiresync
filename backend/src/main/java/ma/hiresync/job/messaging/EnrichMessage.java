package ma.hiresync.job.messaging;

/** Triggers one enrichment batch (20 jobs). triggeredBy is for logging only. */
public record EnrichMessage(String triggeredBy) {}
