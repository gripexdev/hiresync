package ma.hiresync.job.messaging;

/** One scrape task — one message per source pushed to job.scrape.queue. */
public record ScrapeMessage(String source) {}
